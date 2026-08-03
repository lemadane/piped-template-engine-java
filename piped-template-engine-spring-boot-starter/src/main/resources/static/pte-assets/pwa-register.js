(function() {
    var currentScript = document.currentScript || document.querySelector('script[data-pte-service-worker]');
    if (currentScript) {
        var swUrl = currentScript.getAttribute('data-pte-service-worker');
        if (swUrl && 'serviceWorker' in navigator) {
            window.addEventListener('load', function() {
                navigator.serviceWorker.register(swUrl).catch(function(err) {
                    console.error('ServiceWorker registration failed: ', err);
                });
            });
        }
    }
})();
