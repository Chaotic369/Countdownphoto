/* JS <-> native bridge. In a normal browser, WidgetBridge.available is false and the editor runs in demo mode. */
(function () {
  var cap = window.Capacitor;
  var isNative = !!(cap && typeof cap.isNativePlatform === 'function' && cap.isNativePlatform());
  var P = null;
  if (isNative) {
    try { P = cap.registerPlugin ? cap.registerPlugin('WidgetBridge') : (cap.Plugins && cap.Plugins.WidgetBridge); } catch (e) { P = null; }
  }
  var ok = !!P;

  function parse(s) { try { return JSON.parse(s); } catch (e) { return null; } }

  window.WidgetBridge = {
    available: ok,

    /* resolves { widgets: [...], ctx: { mode, appWidgetId, type, widgetId } } */
    init: function () {
      if (!ok) return Promise.resolve({ widgets: [], ctx: { mode: 'app' } });
      return P.init().then(function (r) {
        var list = (r && r.widgets) || [];
        var widgets = list.map(function (s) { return typeof s === 'string' ? parse(s) : s; }).filter(Boolean);
        return { widgets: widgets, ctx: (r && r.ctx) || { mode: 'app' } };
      });
    },

    saveWidget: function (w) {
      if (!ok || !w) return Promise.resolve();
      return P.saveWidget({ config: JSON.stringify(w) }).catch(function () { });
    },

    deleteWidget: function (id) {
      if (!ok) return Promise.resolve();
      return P.deleteWidget({ id: id }).catch(function () { });
    },

    /* resolves { path, url, name } or null */
    savePhoto: function (widgetId, base64, name) {
      if (!ok) return Promise.resolve(null);
      return P.savePhoto({ widgetId: widgetId, base64: base64, name: name || 'photo.jpg' }).then(function (r) {
        if (!r || !r.path) return null;
        var url = cap.convertFileSrc ? cap.convertFileSrc('file://' + r.path) : 'file://' + r.path;
        return { path: r.path, url: url, name: name };
      });
    },

    deletePhoto: function (path) {
      if (!ok || !path) return Promise.resolve();
      return P.deletePhoto({ path: path }).catch(function () { });
    },

    /* asks the launcher to pin a widget of this type to the home screen */
    pinWidget: function (type, widgetId) {
      if (!ok) return Promise.resolve();
      return P.pinWidget({ type: type, widgetId: widgetId }).catch(function () { });
    },

    /* closes the widget config screen with RESULT_OK so the widget is placed */
    finish: function (widgetId) {
      if (!ok) return Promise.resolve();
      return P.finishConfig({ widgetId: widgetId }).catch(function () { });
    }

  };
})();
