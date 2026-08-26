package com.vibe.generated.p20260823;

import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 安全区适配工具（需求规格：屏幕适配 - 必须）
 *
 * - 用 WindowInsetsCompat 获取真实 inset（状态栏 + 底部导航栏/手势条 + 键盘），动态设置 padding
 * - 兜底 padding >= 24dp（top + bottom），防止 inset 回调未触发时内容贴边
 * - 底部把 IME 键盘高度一并计入，输入框不会被键盘遮挡
 * - 单点挂在根容器 root_container 上，覆盖全部页面
 */
public final class Insets {

    private static final int FALLBACK_DP = 24;

    private Insets() {
    }

    public static void applySystemBars(final View root) {
        if (root == null) return;
        final int fallback = dp(root, FALLBACK_DP);

        // 兜底：inset 回调触发前先垫 24dp，内容不贴边
        root.setPadding(fallback, fallback, fallback, fallback);

        ViewCompat.setOnApplyWindowInsetsListener(root,
                new androidx.core.view.OnApplyWindowInsetsListener() {
                    @Override
                    public WindowInsetsCompat onApplyWindowInsets(View v,
                            WindowInsetsCompat insets) {
                        androidx.core.graphics.Insets bars = insets.getInsets(
                                WindowInsetsCompat.Type.systemBars());
                        androidx.core.graphics.Insets ime = insets.getInsets(
                                WindowInsetsCompat.Type.ime());

                        int top = Math.max(bars.top, fallback);
                        int bottom = Math.max(Math.max(bars.bottom, ime.bottom), fallback);
                        int left = Math.max(bars.left, 0);
                        int right = Math.max(bars.right, 0);

                        v.setPadding(left, top, right, bottom);
                        return insets;
                    }
                });

        root.requestApplyInsets();
    }

    private static int dp(View v, int value) {
        return Math.round(v.getResources().getDisplayMetrics().density * value);
    }
}
