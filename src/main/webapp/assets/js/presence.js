/* Nhịp hiện diện chỉ chạy khi trang đang nhìn thấy, không giữ lịch sử người dùng trong RAM/storage. */
(() => {
    'use strict';
    let timer,
        sending = false;
    const pulse = async () => {
        clearTimeout(timer);
        if (!document.hidden && !sending) {
            sending = true;
            try {
                // Nhịp nền vẫn có CSRF/quota nhưng không làm mờ bảng hoặc nháy loading khi người dùng đang đọc.
                const result = await window.VietRoute.mutate('/api/v1/activity/heartbeat', {}, 'post', {
                    uiSilent: true,
                });
                if (result.data.data === false) return;
            } catch (error) {
                /* Quan sát lỗi không che thao tác chính hoặc tự replay request. */
            } finally {
                sending = false;
            }
        }
        timer = setTimeout(pulse, 60000);
    };
    document.addEventListener('visibilitychange', () => {
        if (!document.hidden) pulse();
        else clearTimeout(timer);
    });
    timer = setTimeout(pulse, 15000);
})();
