/* Consent trong bộ nhớ, clearWatch ngay khi đóng/ẩn/offline; không suy chuyển động hoặc tiến độ từ lịch trình. */
((factory) => {
    'use strict';
    const tracking = factory();
    if (typeof module === 'object' && module.exports) module.exports = tracking;
    else window.VietRouteTracking = tracking;
})(() => {
    'use strict';
    // Adapter tiêm phụ thuộc để kiểm từ chối quyền/mạng/race bằng Node, không xin vị trí thật trong test.
    const sharing = ({ start, stop, sample, geolocation, now = Date.now, onState = () => {}, onError = () => {} }) => {
        let generation = 0,
            watch = null,
            session = null,
            opening = false,
            sending = false,
            nextAt = 0;
        const clear = () => {
            if (watch !== null) geolocation.clearWatch(watch);
            watch = null;
        };
        const closeSession = async (value) => {
            if (value) {
                try {
                    await stop(value);
                } catch (error) {
                    onError(error);
                }
            }
        };
        const halt = async () => {
            generation++;
            clear();
            const old = session;
            session = null;
            opening = false;
            onState('STOPPED');
            await closeSession(old);
        };
        const begin = async () => {
            if (opening || session) return;
            opening = true;
            const current = ++generation;
            onState('STARTING');
            try {
                const fresh = await start();
                if (current !== generation) {
                    await closeSession(fresh);
                    return;
                }
                session = fresh;
                nextAt = 0;
                onState('WAITING');
                watch = geolocation.watchPosition(
                    async (position) => {
                        if (current !== generation || !session || sending || now() < nextAt) return;
                        const values = [
                            position.coords.latitude,
                            position.coords.longitude,
                            position.coords.accuracy,
                            position.timestamp,
                        ];
                        if (
                            !values.every(Number.isFinite) ||
                            Math.abs(values[0]) > 90 ||
                            Math.abs(values[1]) > 180 ||
                            values[2] < 0 ||
                            values[2] > 100000
                        )
                            return;
                        if (now() - values[3] > 120000 || values[3] - now() > 15000) {
                            onState('OLD');
                            return;
                        }
                        sending = true;
                        nextAt = now() + fresh.minSampleSeconds * 1000;
                        try {
                            await sample(
                                {
                                    sessionKey: fresh.sessionKey,
                                    latitude: Number(values[0].toFixed(6)),
                                    longitude: Number(values[1].toFixed(6)),
                                    accuracy: Number(values[2].toFixed(2)),
                                    measuredAt: new Date(values[3]).toISOString(),
                                },
                                fresh,
                            );
                            if (current === generation) onState('SHARING');
                        } catch (error) {
                            if (current === generation) {
                                await halt();
                                onError(error);
                            }
                        } finally {
                            sending = false;
                        }
                    },
                    async (error) => {
                        if (current !== generation) return;
                        await halt();
                        onState(error.code === 1 ? 'DENIED' : error.code === 3 ? 'TIMEOUT' : 'UNAVAILABLE');
                    },
                    { enableHighAccuracy: true, maximumAge: 0, timeout: 15000 },
                );
                if (current !== generation) clear();
            } catch (error) {
                if (current === generation) {
                    await halt();
                    onError(error);
                }
            } finally {
                if (current === generation) opening = false;
            }
        };
        return Object.freeze({ begin, halt, isActive: () => opening || session !== null });
    };

    const attach = (root, { driver = false } = {}) => {
        if (!root) return null;
        const { api, mutate, feedback, failure } = window.VietRoute,
            labels = root.dataset,
            status = root.querySelector('[data-tracking-status]'),
            summary = root.querySelector('[data-tracking-summary]'),
            trail = root.querySelector('[data-tracking-trail]'),
            startButton = root.querySelector('[data-tracking-start]'),
            stopButton = root.querySelector('[data-tracking-stop]'),
            refreshButton = root.querySelector('[data-tracking-refresh]'),
            feedbackNode = root.querySelector('[data-tracking-feedback]'),
            dialog = root.closest('dialog'),
            locale = document.documentElement.lang.startsWith('en') ? 'en-GB' : 'vi-VN';
        let context = null,
            timer = null,
            reading = false,
            pending = false,
            readError = false,
            epoch = 0,
            disabled = false;
        const text = (tag, value) => {
            const element = document.createElement(tag);
            element.textContent = value;
            return element;
        };
        const date = (value) =>
            new Intl.DateTimeFormat(locale, {
                dateStyle: 'short',
                timeStyle: 'medium',
                timeZone: 'Asia/Ho_Chi_Minh',
            }).format(new Date(value));
        const sync = () => {
            if (startButton) {
                startButton.hidden = !context?.canWrite;
                startButton.disabled = pending || disabled || sender.isActive() || !context?.canStart;
                stopButton.hidden = !context?.canWrite;
                stopButton.disabled = pending || !sender.isActive();
            }
            refreshButton.disabled = reading || pending || !context;
        };
        const wipe = () => {
            summary.replaceChildren();
            trail.replaceChildren();
        };
        const read = async () => {
            if (reading || pending || !context || !dialog.open || document.hidden) return;
            const active = context,
                current = epoch;
            reading = true;
            sync();
            try {
                const value = (await api.get(active.path, { timeout: 10000, uiSilent: true })).data.data;
                if (current !== epoch || !dialog.open) return;
                if (readError) {
                    feedback('', false, feedbackNode.id);
                    readError = false;
                }
                disabled = value.status === 'DISABLED';
                status.textContent = labels[value.status.toLowerCase()] || value.status;
                wipe();
                if (value.lastReceivedAt)
                    summary.append(text('p', `${labels.received}: ${date(value.lastReceivedAt)}`));
                if (value.latest) {
                    const point = value.latest;
                    summary.append(
                        text('p', `${labels.coordinates}: ${point.latitude}, ${point.longitude}`),
                        text(
                            'p',
                            `${labels.accuracy}: ${point.accuracy} m · ${labels.measured}: ${date(point.measuredAt)}`,
                        ),
                    );
                    for (const point of value.trail.slice(0, 20))
                        trail.append(
                            text(
                                'li',
                                `${date(point.measuredAt)} · ${point.latitude}, ${point.longitude} · ±${point.accuracy} m`,
                            ),
                        );
                }
                if (sender.isActive() && ['OFF', 'CLOSED', 'DISABLED', 'STALE'].includes(value.status)) {
                    wipe();
                    await sender.halt();
                }
            } catch (error) {
                if (current === epoch) {
                    wipe();
                    await sender.halt();
                    if (current !== epoch || !dialog.open) return;
                    status.textContent = labels.offline;
                    readError = true;
                    failure(error, feedbackNode.id);
                }
            } finally {
                reading = false;
                sync();
            }
        };
        const sender = sharing({
            geolocation: navigator.geolocation,
            start: async () => {
                const active = context;
                return {
                    ...(await mutate(`${active.path}/start`, { tripVersion: active.version })).data.data,
                    path: active.path,
                };
            },
            stop: async (session) => {
                await mutate(`${session.path}/stop`, { sessionKey: session.sessionKey });
            },
            sample: async (body, session) => {
                await mutate(`${session.path}/samples`, body, 'post', { uiSilent: true });
                void read();
            },
            onState: (state) => {
                if (dialog.open) status.textContent = labels[state.toLowerCase()] || state;
                sync();
            },
            onError: (error) => {
                readError = false;
                if (dialog.open) failure(error, feedbackNode.id);
            },
        });
        startButton?.addEventListener('click', async () => {
            if (!context?.canWrite || pending || !dialog.open) return;
            // GET cũ không được thu hồi consent vừa bật sau khi request đó bắt đầu.
            epoch++;
            readError = false;
            feedback('', false, feedbackNode.id);
            if (!window.isSecureContext || !navigator.geolocation) {
                status.textContent = labels.unsupported;
                return;
            }
            pending = true;
            sync();
            try {
                await sender.begin();
            } finally {
                pending = false;
                sync();
                void read();
            }
        });
        stopButton?.addEventListener('click', async () => {
            if (pending) return;
            epoch++;
            pending = true;
            const stopped = sender.halt();
            wipe();
            sync();
            try {
                await stopped;
            } finally {
                pending = false;
                sync();
            }
        });
        refreshButton.addEventListener('click', () => void read());
        const close = () => {
            epoch++;
            if (timer) clearInterval(timer);
            timer = null;
            void sender.halt();
            context = null;
            readError = false;
            wipe();
            root.hidden = true;
        };
        dialog.addEventListener('close', close);
        const pause = () => {
            epoch++;
            void sender.halt();
            wipe();
            if (dialog.open) status.textContent = labels.paused;
        };
        document.addEventListener('visibilitychange', () => {
            if (document.hidden) pause();
            else void read();
        });
        window.addEventListener('offline', pause);
        window.addEventListener('pagehide', pause);
        // Không tự bật lại khi online/trang hiện lại; người lái phải chủ động consent lần nữa.
        return Object.freeze({
            open(value) {
                close();
                context = value;
                disabled = false;
                root.hidden = !value;
                if (!value) return;
                feedback('', false, feedbackNode.id);
                status.textContent = labels.loading;
                sync();
                timer = setInterval(() => void read(), 20000);
                void read();
            },
            update(value) {
                context = value;
                if (!value || ['CANCELLED', 'COMPLETED'].includes(value.status)) {
                    void sender.halt();
                    wipe();
                }
                sync();
                void read();
            },
        });
    };
    return Object.freeze({ sharing, attach });
});
