document.addEventListener('DOMContentLoaded', function () {
    const currentPortElement = document.getElementById('currentPort');
    if (currentPortElement) {
        currentPortElement.textContent = window.location.port || '8088';
    }
});

