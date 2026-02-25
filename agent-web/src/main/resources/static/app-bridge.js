/**
 * App Bridge — Web default stub
 *
 * Provides a safe no-op appBridge for web browsers.
 * In HarmonyOS / native App environments, the native layer injects its own
 * bridge object BEFORE this script runs, so the early-return guard preserves it.
 */
(function () {
    // Native bridge already injected — nothing to do
    if (window.appBridge) return;

    window.appBridge = {
        isApp: false,
        platform: 'web',

        // Title
        setTitle: function (title) { document.title = title; },

        // Toast → console
        showToast: function (msg, duration) { console.log('[AppBridge Toast]', msg); },

        // Vibration → no-op
        vibrate: function (ms) { /* web: no native vibration */ },

        // Recording → always fall through to Web API
        startRecording: function () { return Promise.resolve({ useWeb: true }); },
        stopRecording:  function () { return Promise.resolve({ useWeb: true, audioPath: null }); },

        // Audio playback → always fall through to Web Audio
        playAudio: function (url) { return Promise.resolve({ useWeb: true }); }
    };

    console.log('[AppBridge] Web stub initialized — running in browser');
})();
