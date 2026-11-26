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
    time::Duration,
};

#[derive(Default)]
struct Model {
    x: f64,
    y: f64,
    w: i32,
    h: i32,
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
    controlled: bool,
}
static RUNTIME: OnceLock<Mutex<Runtime>> = OnceLock::new();
static ACTIONS: OnceLock<(Mutex<VecDeque<String>>, Condvar)> = OnceLock::new();
fn runtime() -> &'static Mutex<Runtime> {
    RUNTIME.get_or_init(|| {
        Mutex::new(Runtime {
            service: None,
            platform: Arc::new(Android::default()),
            base: String::new(),
            controlled: false,
        })
    })
}
fn queue() -> &'static (Mutex<VecDeque<String>>, Condvar) {
    ACTIONS.get_or_init(|| (Mutex::new(VecDeque::new()), Condvar::new()))
}
fn push(v: Value) {
    let (q, cv) = queue();
    q.lock().unwrap().push_back(v.to_string());
    cv.notify_one();
}
fn observe(r: &mut Runtime) {
    let controlled = r
        .service
        .as_ref()
        .is_some_and(|s| s.status().state.mode == "controlled");
    if r.controlled && !controlled {
        push(json!({"cursor":{"visible":false}}));
    }
    r.controlled = controlled;
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
        push(self.model.lock().unwrap().position(x, y));
    }
    fn start_capture(&self, _: std::sync::mpsc::Sender<InputEvent>) -> anyhow::Result<()> {
        anyhow::bail!("Android receives only")
    }
    fn stop_capture(&self) {}
    fn inject(&self, ev: &InputEvent) {
        push(self.model.lock().unwrap().action(ev));
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
        let mut c: NetConfig = serde_json::from_str(&config)?;
        c.config_dir = base.clone().into();
        if c.name.is_empty() {
            c.name = name;
        }
        let mut r = runtime().lock().unwrap();
        if let Some(s) = r.service.take() {
            s.stop();
        }
        queue().0.lock().unwrap().clear();
        *r.platform.model.lock().unwrap() = Model {
            w,
            h,
            ..Default::default()
        };
        let hooks = Hooks {
            switch_logitech: Box::new(|ch| push(json!({"switch":ch}))),
            on_paired: Box::new(|p| push(json!({"paired":p}))),
            on_unpaired: Box::new(|id| push(json!({"unpaired":id}))),
            ..Hooks::default()
        };
        // Android cannot capture input, so it only ever receives: always a sub.
        c.role = flock_net::Role::Sub;
        r.base = base;
        r.service = Some(Service::start(c, r.platform.clone(), hooks)?);
        r.controlled = false;
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
    observe(&mut r);
    push(json!({"cursor":{"visible":false}}));
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
    if let (Ok(mut c), Some(s)) = (serde_json::from_str::<NetConfig>(&text), &r.service) {
        c.config_dir = r.base.clone().into();
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
#[no_mangle]
pub extern "system" fn Java_io_github_wwhalefe_flockswitch_net_NetBridge_status(
    env: JNIEnv,
    _: JObject,
) -> jstring {
    let mut r = runtime().lock().unwrap();
    observe(&mut r);
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
    // Poll control state even when no input or settings window is active.
    let deadline = std::time::Instant::now() + Duration::from_millis(ms.clamp(0, 5000) as u64);
    loop {
        observe(&mut runtime().lock().unwrap());
        let (q, cv) = queue();
        let mut q = q.lock().unwrap();
        if let Some(s) = q.pop_front() {
            return java(&env, Some(s));
        }
        let left = deadline.saturating_duration_since(std::time::Instant::now());
        if left.is_zero() {
            return null_mut();
        }
        q = cv
            .wait_timeout(q, left.min(Duration::from_millis(20)))
            .unwrap()
            .0;
        drop(q);
    }
}
#[cfg(test)]
mod tests {
    use super::*;
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
