/*
 *    sora-editor - the awesome code editor for Android
 *    https://github.com/Rosemoe/sora-editor
 *    Copyright (C) 2020-2024  Rosemoe
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 *
 *     Please contact Rosemoe by email 2073412493@qq.com if you need
 *     additional information or have any questions
 */
package io.github.rosemoe.sora.widget.component;

import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import io.github.rosemoe.sora.R;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * Default adapter to display results
 *
 * @author Rose
 */
public final class DefaultCompletionItemAdapter extends EditorCompletionAdapter {

    @Override
    public int getItemHeight() {
        // 45 dp
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 45, getContext().getResources().getDisplayMetrics());
    }

    @Override
    public View getView(int pos, View view, ViewGroup parent, boolean isCurrentCursorPosition) {
        if (view == null) {
            view = LayoutInflater.from(getContext()).inflate(R.layout.default_completion_result_item, parent, false);
        }
        var item = getItem(pos);

        // 补全文字颜色根据补全窗口背景亮度自适应，不依赖 COMPLETION_WND_TEXT_PRIMARY。
        // 当编辑器使用 TextMateColorScheme 时，其 onChangeTheme 回调会 colors.clear() 清空
        // 我们 setColor 写入的值并重置为默认（暗色判断不准时为黑色），导致暗色下补全文字看不清。
        // 直接按背景算颜色可彻底绕开该问题：暗色背景→白字，亮色背景→黑字。
        int bgColor = getThemeColor(EditorColorScheme.COMPLETION_WND_BACKGROUND);
        boolean dark = isColorDark(bgColor);
        int primaryColor = dark ? 0xFFFFFFFF : 0xFF000000;
        int secondaryColor = dark ? 0xFFB0B0B0 : 0xFF616161;

        TextView tv = view.findViewById(R.id.result_item_label);
        tv.setText(item.label);
        tv.setTextColor(primaryColor);

        tv = view.findViewById(R.id.result_item_desc);
        tv.setText(item.desc);
        tv.setTextColor(secondaryColor);

        view.setTag(pos);
        if (isCurrentCursorPosition) {
            view.setBackgroundColor(getThemeColor(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT));
        } else {
            view.setBackgroundColor(0);
        }
        ImageView iv = view.findViewById(R.id.result_item_image);
        iv.setImageDrawable(item.icon);
        return view;
    }

    private static boolean isColorDark(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (r * 0.299 + g * 0.587 + b * 0.114) < 128;
    }

}
