use flock_net::{Hooks, InputEvent, NetConfig, Os, Platform, Rect, Service};
use jni::{
    objects::{JObject, JString},
    sys::{jboolean, jint, jstring},
    JNIEnv,
};
use serde_json::{json, Value};
use std::{
    collections::VecDeque,
    ptr::null_mut,
    sync::{Arc, Condvar, Mutex, OnceLock},
    time::{Duration, Instant},
};

#[derive(Default)]
struct Model {
    x: f64,
    y: f64,
    w: i32,
    h: i32,
    controlled: bool,
}
impl Model {
    fn position(&mut self, x: f64, y: f64) -> Value {
        self.x = if x.is_finite() {
            x.clamp(0., (self.w - 1).max(0) as f64)
        } else {
            self.x
        };
        self.y = if y.is_finite() {
            y.clamp(0., (self.h - 1).max(0) as f64)
        } else {
            self.y
        };
        json!({"cursor":{"x":self.x,"y":self.y,"visible":true}})
    }
    fn control_warp(&mut self, x: f64, y: f64) -> Value {
        // Android cannot capture, so netcore only warps on an accepted Enter and
        // on the matching local() return. Record both boundaries before publish(),
        // including sessions that begin and end between status snapshots.
        self.controlled = !self.controlled;
        let position = self.position(x, y);
        if self.controlled {
            position
        } else {
            json!({"cursor":{"visible":false}})
        }
    }
    fn action(&mut self, event: &InputEvent) -> Value {
        match event {
            InputEvent::Motion { dx, dy } => self.position(self.x + dx, self.y + dy),
            _ => serde_json::to_value(event).unwrap(),
        }
    }
}
#[derive(Default)]
struct Android {
    model: Mutex<Model>,
    ready: Mutex<(bool, String)>,
}
struct Runtime {
    service: Option<Service>,
    platform: Arc<Android>,
    base: String,
}
static RUNTIME: OnceLock<Mutex<Runtime>> = OnceLock::new();
static ACTIONS: OnceLock<Actions> = OnceLock::new();
const RETAINED_ACTIONS: usize = 32;

#[derive(Default)]
struct Actions {
    pending: Mutex<VecDeque<String>>,
    ready: Condvar,
}

impl Actions {
    fn push(&self, value: Value) {
        self.pending.lock().unwrap().push_back(value.to_string());
        self.ready.notify_one();
    }

    fn reset(&self, value: Option<Value>) {
        // Drop both queued payloads and the allocation retained after a burst.
        *self.pending.lock().unwrap() = value.into_iter().map(|v| v.to_string()).collect();
        self.ready.notify_one();
    }

    fn next(&self, timeout: Duration) -> Option<String> {
        let deadline = Instant::now() + timeout;
        loop {
            let mut pending = self.pending.lock().unwrap();
            if let Some(action) = pending.pop_front() {
                if pending.is_empty() && pending.capacity() > RETAINED_ACTIONS {
                    pending.shrink_to(RETAINED_ACTIONS);
                }
                return Some(action);
            }
            let left = deadline.saturating_duration_since(Instant::now());
            if left.is_zero() {
                return None;
            }
            pending = self.ready.wait_timeout(pending, left).unwrap().0;
            drop(pending);
        }
    }
}
fn runtime() -> &'static Mutex<Runtime> {
    RUNTIME.get_or_init(|| {
        Mutex::new(Runtime {
            service: None,
            platform: Arc::new(Android::default()),
            base: String::new(),
        })
    })
}
fn queue() -> &'static Actions {
    ACTIONS.get_or_init(Actions::default)
}
fn push(v: Value) {
    queue().push(v);
}
impl Platform for Android {
    fn os(&self) -> Os {
        Os::Android
    }
    fn displays(&self) -> Vec<Rect> {
        let m = self.model.lock().unwrap();
        vec![Rect::new(0., 0., m.w.max(1) as f64, m.h.max(1) as f64)]
    }
    fn cursor(&self) -> Option<(f64, f64)> {
        let m = self.model.lock().unwrap();
        Some((m.x, m.y))
    }
    fn warp(&self, x: f64, y: f64) {
        push(self.model.lock().unwrap().control_warp(x, y));
    }
    fn start_capture(&self, _: std::sync::mpsc::Sender<InputEvent>) -> anyhow::Result<()> {
        anyhow::bail!("Android receives only")
    }
    fn stop_capture(&self) {}
    fn inject(&self, ev: &InputEvent) {
        push(self.model.lock().unwrap().action(ev));
    }
    fn set_clipboard_text(&self, text: &str) {
        push(json!({"clipboard": {"text": text}}));
    }
    fn ready(&self) -> Result<(), String> {
        let r = self.ready.lock().unwrap();
        if r.0 {
            Ok(())
        } else {
            Err(r.1.clone())
        }
    }
}
fn string(env: &mut JNIEnv, s: JString) -> String {
    env.get_string(&s).map(Into::into).unwrap_or_default()
}
fn java(env: &JNIEnv, s: Option<String>) -> jstring {
    s.and_then(|s| env.new_string(s).ok())
        .map(|s| s.into_raw())
        .unwrap_or(null_mut())
}
// Both JNI entry points use the same receive-only config boundary.
fn android_config(text: &str, base: &str) -> serde_json::Result<NetConfig> {
    let mut c: NetConfig = serde_json::from_str(text)?;
    c.role = flock_net::Role::Sub;
    c.config_dir = base.into();
    Ok(c)
}
fn android_hooks() -> Hooks {
    Hooks {
        switch_logitech: Box::new(|ch| push(json!({"switch":ch}))),
        on_paired: Box::new(|p| push(json!({"paired":p}))),
        on_unpaired: Box::new(|id| push(json!({"unpaired":id}))),
        on_peer_changed: Box::new(|p| push(json!({"peer_changed":p}))),
        ..Hooks::default()
    }
}
#[no_mangle]
pub extern "system" fn Java_io_github_wwhalefe_flockswitch_net_NetBridge_start(
    mut env: JNIEnv,
    _: JObject,
    config: JString,
    base: JString,
    name: JString,
    w: jint,
    h: jint,
) -> jstring {
    let config = string(&mut env, config);
    let base = string(&mut env, base);
    let name = string(&mut env, name);
    let result = (|| -> anyhow::Result<()> {
        let mut c = android_config(&config, &base)?;
        if c.name.is_empty() {
            c.name = name;
        }
        let mut r = runtime().lock().unwrap();
        if let Some(s) = r.service.take() {
            s.stop();
        }
        queue().reset(None);
        *r.platform.model.lock().unwrap() = Model {
            w,
            h,
            ..Default::default()
        };
        r.base = base;
        r.service = Some(Service::start(c, r.platform.clone(), android_hooks())?);
        Ok(())
    })();
    java(&env, result.err().map(|e| e.to_string()))
}
#[no_mangle]
pub extern "system" fn Java_io_github_wwhalefe_flockswitch_net_NetBridge_stop(
    _: JNIEnv,
    _: JObject,
) {
    let mut r = runtime().lock().unwrap();
    if let Some(s) = r.service.take() {
        s.stop();
    }
    queue().reset(Some(json!({"cursor":{"visible":false}})));
}
#[no_mangle]
pub extern "system" fn Java_io_github_wwhalefe_flockswitch_net_NetBridge_setScreen(
    _: JNIEnv,
    _: JObject,
    w: jint,
    h: jint,
) {
    let r = runtime().lock().unwrap();
    let mut m = r.platform.model.lock().unwrap();
    m.w = w.max(1);
    m.h = h.max(1);
    let (x, y) = (m.x, m.y);
    m.position(x, y);
}
#[no_mangle]
pub extern "system" fn Java_io_github_wwhalefe_flockswitch_net_NetBridge_setReady(
    mut env: JNIEnv,
    _: JObject,
    ok: jboolean,
    reason: JString,
) {
    let reason = if reason.is_null() {
        "Accessibility service is disconnected".into()
    } else {
        string(&mut env, reason)
    };
    *runtime().lock().unwrap().platform.ready.lock().unwrap() = (ok != 0, reason);
}
#[no_mangle]
pub extern "system" fn Java_io_github_wwhalefe_flockswitch_net_NetBridge_updateConfig(
    mut env: JNIEnv,
    _: JObject,
    c: JString,
) {
    let text = string(&mut env, c);
    let r = runtime().lock().unwrap();
    if let (Ok(c), Some(s)) = (android_config(&text, &r.base), &r.service) {
        let _ = s.update_config(c);
    }
}
#[no_mangle]
pub extern "system" fn Java_io_github_wwhalefe_flockswitch_net_NetBridge_request(
    mut env: JNIEnv,
    _: JObject,
    c: JString,
) {
    let text = string(&mut env, c);
    let r = runtime().lock().unwrap();
    if let (Ok(req), Some(s)) = (serde_json::from_str(&text), &r.service) {
        let _ = s.request(req);
    }
}
/// Redials the master now (after unlock, screen on or a network change).
#[no_mangle]
pub extern "system" fn Java_io_github_wwhalefe_flockswitch_net_NetBridge_reconnect(
    _: JNIEnv,
    _: JObject,
) {
    let r = runtime().lock().unwrap();
    if let Some(s) = &r.service {
        let _ = s.reconnect();
    }
}
/// Sends edited key rules (a JSON object "<src>><dst>" -> rules) to the master.
#[no_mangle]
pub extern "system" fn Java_io_github_wwhalefe_flockswitch_net_NetBridge_sendKeyRules(
    mut env: JNIEnv,
    _: JObject,
    c: JString,
) {
    let text = string(&mut env, c);
    let r = runtime().lock().unwrap();
    if let (Ok(keys), Some(s)) = (serde_json::from_str(&text), &r.service) {
        let _ = s.send_key_rules(keys);
    }
}
#[no_mangle]
pub extern "system" fn Java_io_github_wwhalefe_flockswitch_net_NetBridge_status(
    env: JNIEnv,
    _: JObject,
) -> jstring {
    let r = runtime().lock().unwrap();
    java(
        &env,
        Some(
            r.service
                .as_ref()
                .map(|s| serde_json::to_string(&s.status()).unwrap())
                .unwrap_or_else(|| "{\"running\":false}".into()),
        ),
    )
}
#[no_mangle]
pub extern "system" fn Java_io_github_wwhalefe_flockswitch_net_NetBridge_nextAction(
    env: JNIEnv,
    _: JObject,
    ms: jint,
) -> jstring {
    let action = queue().next(Duration::from_millis(ms.clamp(0, 5000) as u64));
    java(&env, action)
}
#[cfg(test)]
mod tests {
    use super::*;
    static GLOBAL_ACTIONS: Mutex<()> = Mutex::new(());

    #[test]
    fn return_warp_hides_cursor_even_when_status_misses_the_session() {
        let _guard = GLOBAL_ACTIONS.lock().unwrap();
        let platform = Android::default();
        for consume_enter_first in [true, false] {
            queue().reset(None);
            platform.warp(0., 0.);
            if consume_enter_first {
                let enter = queue().next(Duration::ZERO).unwrap();
                assert_eq!(
                    serde_json::from_str::<Value>(&enter).unwrap()["cursor"]["visible"],
                    true
                );
            }

            // netcore calls warp again from local(), even if neither JNI consumer
            // observed the intervening controlled status.
            platform.warp(0., 0.);
            if !consume_enter_first {
                let enter = queue().next(Duration::ZERO).unwrap();
                assert_eq!(
                    serde_json::from_str::<Value>(&enter).unwrap()["cursor"]["visible"],
                    true
                );
            }
            let leave = queue().next(Duration::ZERO).unwrap();
            assert_eq!(
                serde_json::from_str::<Value>(&leave).unwrap()["cursor"]["visible"],
                false
            );
            assert_eq!(queue().next(Duration::ZERO), None);
        }
    }

    #[test]
    fn motion_and_key_release_preserve_control_boundary_order() {
        let _guard = GLOBAL_ACTIONS.lock().unwrap();
        queue().reset(None);
        let platform = Android {
            model: Mutex::new(Model {
                w: 100,
                h: 50,
                ..Default::default()
            }),
            ..Default::default()
        };
        let key = |down| InputEvent::Key { usage: 4, down };
        platform.warp(0., 10.);
        platform.inject(&key(true));
        platform.inject(&InputEvent::Motion { dx: 20., dy: 5. });
        platform.warp(0., 15.);
        platform.inject(&key(false));
        platform.warp(99., 25.);
        platform.warp(99., 25.);

        for expected in [
            json!({"cursor":{"x":0.,"y":10.,"visible":true}}),
            json!({"type":"Key","usage":4,"down":true}),
            json!({"cursor":{"x":20.,"y":15.,"visible":true}}),
            json!({"cursor":{"visible":false}}),
            json!({"type":"Key","usage":4,"down":false}),
            json!({"cursor":{"x":99.,"y":25.,"visible":true}}),
            json!({"cursor":{"visible":false}}),
        ] {
            let action = queue().next(Duration::ZERO).unwrap();
            assert_eq!(serde_json::from_str::<Value>(&action).unwrap(), expected);
        }
        assert_eq!(queue().next(Duration::ZERO), None);
    }

    #[test]
    fn empty_actions_respect_the_deadline() {
        let actions = Actions::default();
        for timeout in [Duration::ZERO, Duration::from_millis(5)] {
            let start = Instant::now();
            assert_eq!(actions.next(timeout), None);
            assert!(start.elapsed() >= timeout);
        }
    }

    #[test]
    fn actions_preserve_order_and_stop_releases_the_backlog() {
        let actions = Actions::default();
        for i in 0..1024 {
            actions.push(json!({"clipboard":{"text":format!("payload-{i}")}}));
        }
        for i in 0..2 {
            let action = actions.next(Duration::ZERO).unwrap();
            assert_eq!(
                serde_json::from_str::<Value>(&action).unwrap()["clipboard"]["text"],
                format!("payload-{i}")
            );
        }
        let hide = json!({"cursor":{"visible":false}});
        actions.reset(Some(hide.clone()));
        assert_eq!(actions.pending.lock().unwrap().len(), 1);
        assert!(actions.pending.lock().unwrap().capacity() < 1024);
        assert_eq!(actions.next(Duration::ZERO), Some(hide.to_string()));
        actions.reset(None);
        assert_eq!(actions.pending.lock().unwrap().capacity(), 0);
        assert_eq!(actions.next(Duration::ZERO), None);
    }

    #[test]
    fn drained_bursts_retain_at_most_thirty_two_slots() {
        let actions = Actions::default();
        for i in 0..1024 {
            actions.push(json!({"type":"Key","usage":i,"down":true}));
        }
        assert!(actions.pending.lock().unwrap().capacity() >= 1024);
        for i in 0..1024 {
            let action = actions.next(Duration::ZERO).unwrap();
            assert_eq!(serde_json::from_str::<Value>(&action).unwrap()["usage"], i);
        }
        let pending = actions.pending.lock().unwrap();
        assert!(pending.is_empty());
        assert!(pending.capacity() <= RETAINED_ACTIONS);
    }

    #[test]
    fn input_and_stop_wake_an_idle_consumer() {
        for stop in [false, true] {
            let actions = Actions::default();
            let (tx, rx) = std::sync::mpsc::channel();
            std::thread::scope(|scope| {
                let waiter = scope.spawn(|| {
                    let _ = tx.send(());
                    actions.next(Duration::from_secs(5))
                });
                rx.recv_timeout(Duration::from_secs(1)).unwrap();
                let value = json!({"cursor":{"visible":!stop}});
                if stop {
                    actions.reset(Some(value.clone()));
                } else {
                    actions.push(value.clone());
                }
                assert_eq!(waiter.join().unwrap(), Some(value.to_string()));
            });
        }
    }
    #[test]
    fn android_config_always_receives_and_preserves_consent() {
        for role in ["master", "sub"] {
            let c = android_config(
                &format!(r#"{{"on":true,"notice_accepted":true,"role":"{role}"}}"#),
                "/tmp/android",
            )
            .unwrap();
            assert_eq!(c.role, flock_net::Role::Sub);
            assert!(c.on && c.notice_accepted);
            assert_eq!(c.config_dir, std::path::PathBuf::from("/tmp/android"));
        }
        let legacy = android_config(r#"{"on":true}"#, "/tmp/android").unwrap();
        assert_eq!(legacy.role, flock_net::Role::Sub);
        assert!(!legacy.notice_accepted);
    }

    #[test]
    fn peer_change_hook_queues_the_updated_peer() {
        let _guard = GLOBAL_ACTIONS.lock().unwrap();
        let p = flock_net::PeerConfig {
            id: "master".into(),
            fingerprint: "trusted".into(),
            last_addr: "10.0.0.2:47160".into(),
            ..Default::default()
        };
        (android_hooks().on_peer_changed)(p.clone());
        let mut q = queue().pending.lock().unwrap();
        let action: Value = serde_json::from_str(&q.pop_front().unwrap()).unwrap();
        assert_eq!(action, json!({"peer_changed":p}));
    }
    #[test]
    fn clamp_and_motion() {
        let mut m = Model {
            w: 100,
            h: 50,
            ..Default::default()
        };
        assert_eq!(
            m.position(-2., 80.),
            json!({"cursor":{"x":0.,"y":49.,"visible":true}})
        );
        m.action(&InputEvent::Motion {
            dx: 200.,
            dy: -100.,
        });
        assert_eq!((m.x, m.y), (99., 0.));
        m.position(f64::NAN, f64::INFINITY);
        assert_eq!((m.x, m.y), (99., 0.));
    }
    #[test]
    fn events_keep_core_schema() {
        let mut m = Model::default();
        assert_eq!(
            m.action(&InputEvent::Key {
                usage: 4,
                down: true
            }),
            json!({"type":"Key","usage":4,"down":true})
        );
        assert_eq!(
            m.action(&InputEvent::Button {
                button: flock_net::MouseButton::Left,
                down: false
            }),
            json!({"type":"Button","button":"Left","down":false})
        );
        assert_eq!(
            m.action(&InputEvent::Scroll { dx: 0.5, dy: -1. }),
            json!({"type":"Scroll","dx":0.5,"dy":-1.})
        );
    }
}
