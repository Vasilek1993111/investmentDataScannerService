(() => {
    function getCookie(name) {
        const escapedName = name.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, '\\$&');
        const match = document.cookie.match(new RegExp(`(?:^|; )${escapedName}=([^;]*)`));
        return match ? decodeURIComponent(match[1]) : null;
    }

    function normalizeHeaders(headers = {}) {
        if (headers instanceof Headers) {
            return headers;
        }

        const normalized = new Headers();
        Object.entries(headers).forEach(([key, value]) => {
            if (value !== undefined && value !== null) {
                normalized.set(key, value);
            }
        });
        return normalized;
    }

    function withCsrf(init = {}) {
        const nextInit = { ...init };
        const method = (nextInit.method || 'GET').toUpperCase();
        nextInit.credentials = nextInit.credentials || 'same-origin';

        if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
            const token = getCookie('XSRF-TOKEN');
            const headers = normalizeHeaders(nextInit.headers);
            if (token) {
                headers.set('X-XSRF-TOKEN', token);
            }
            nextInit.headers = headers;
        }

        return nextInit;
    }

    const originalFetch = window.fetch.bind(window);
    window.fetch = (input, init = {}) => originalFetch(input, withCsrf(init));

    window.securityUtils = {
        getCookie,
        withCsrf,
        buildWebSocketUrl(path) {
            const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            return `${protocol}//${window.location.host}${path}`;
        }
    };
})();
