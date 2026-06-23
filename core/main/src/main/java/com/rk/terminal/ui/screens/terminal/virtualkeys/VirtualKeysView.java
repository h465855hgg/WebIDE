package com.rk.terminal.ui.screens.terminal.virtualkeys;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings; // 注意：这里引用的是安卓系统的 Settings
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A {@link View} showing extra keys (such as Escape, Ctrl, Alt) not normally available on an Android soft
 * keyboards.
 */
public final class VirtualKeysView extends GridLayout {

  public static final int DEFAULT_BUTTON_TEXT_COLOR = 0xFFFFFFFF;
  public static final int DEFAULT_BUTTON_ACTIVE_TEXT_COLOR = 0xFFf44336;
  public static final int DEFAULT_BUTTON_BACKGROUND_COLOR = 0x00000000;
  public static final int DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR = 0xFF7F7F7F;
  public static final int MIN_LONG_PRESS_DURATION = 200;
  public static final int MAX_LONG_PRESS_DURATION = 3000;
  public static final int FALLBACK_LONG_PRESS_DURATION = 400;
  public static final int MIN_LONG_PRESS__REPEAT_DELAY = 5;
  public static final int MAX_LONG_PRESS__REPEAT_DELAY = 2000;
  public static final int DEFAULT_LONG_PRESS_REPEAT_DELAY = 80;

  private IVirtualKeysView mVirtualKeysViewClient;
  private Map<SpecialButton, SpecialButtonState> mSpecialButtons;
  private Set<String> mSpecialButtonsKeys;
  private List<String> mRepetitiveKeys;
  private int mButtonTextColor;
  private int mButtonActiveTextColor;
  private int mButtonBackgroundColor;
  private int mButtonActiveBackgroundColor;
  private boolean mButtonTextAllCaps = true;
  private int mLongPressTimeout;
  private int mLongPressRepeatDelay;
  private PopupWindow mPopupWindow;

  private ScheduledExecutorService mScheduledExecutor;
  private Handler mHandler;
  private SpecialButtonsLongHoldRunnable mSpecialButtonsLongHoldRunnable;
  private int mLongPressCount;

  public VirtualKeysView(Context context, AttributeSet attrs) {
    super(context, attrs);

    setRepetitiveKeys(VirtualKeysConstants.PRIMARY_REPETITIVE_KEYS);
    setSpecialButtons(getDefaultSpecialButtons(this));
    setButtonColors(
            DEFAULT_BUTTON_TEXT_COLOR,
            DEFAULT_BUTTON_ACTIVE_TEXT_COLOR,
            DEFAULT_BUTTON_BACKGROUND_COLOR,
            DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR);
    setLongPressTimeout(ViewConfiguration.getLongPressTimeout());
    setLongPressRepeatDelay(DEFAULT_LONG_PRESS_REPEAT_DELAY);
  }

  public void setButtonColors(
          int buttonTextColor,
          int buttonActiveTextColor,
          int buttonBackgroundColor,
          int buttonActiveBackgroundColor) {
    mButtonTextColor = buttonTextColor;
    mButtonActiveTextColor = buttonActiveTextColor;
    mButtonBackgroundColor = buttonBackgroundColor;
    mButtonActiveBackgroundColor = buttonActiveBackgroundColor;
  }

  /**
   * 更新所有已创建按钮的文字颜色。
   * 用于主题切换时动态更新按钮颜色，无需 reload() 重建整个布局。
   * 特殊按钮（CTRL/ALT等）处于激活态时使用 mButtonActiveTextColor。
   */
  public void updateAllButtonColors() {
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      if (child instanceof Button) {
        Button button = (Button) child;
        boolean isActiveSpecial = false;
        for (SpecialButtonState state : mSpecialButtons.values()) {
          if (state.isActive && state.buttons.contains(button)) {
            isActiveSpecial = true;
            break;
          }
        }
        button.setTextColor(isActiveSpecial ? mButtonActiveTextColor : mButtonTextColor);
      }
    }
  }

  @NonNull
  public Map<SpecialButton, SpecialButtonState> getDefaultSpecialButtons(
          VirtualKeysView extraKeysView) {
    return new HashMap<SpecialButton, SpecialButtonState>() {
      {
        put(SpecialButton.CTRL, new SpecialButtonState(extraKeysView));
        put(SpecialButton.ALT, new SpecialButtonState(extraKeysView));
        put(SpecialButton.SHIFT, new SpecialButtonState(extraKeysView));
        put(SpecialButton.FN, new SpecialButtonState(extraKeysView));
      }
    };
  }

  public IVirtualKeysView getVirtualKeysViewClient() {
    return mVirtualKeysViewClient;
  }

  public void setVirtualKeysViewClient(IVirtualKeysView extraKeysViewClient) {
    mVirtualKeysViewClient = extraKeysViewClient;
  }

  public List<String> getRepetitiveKeys() {
    if (mRepetitiveKeys == null) return null;
    return mRepetitiveKeys.stream().map(String::new).collect(Collectors.toList());
  }

  public void setRepetitiveKeys(@NonNull List<String> repetitiveKeys) {
    mRepetitiveKeys = repetitiveKeys;
  }

  public Map<SpecialButton, SpecialButtonState> getSpecialButtons() {
    if (mSpecialButtons == null) return null;
    return mSpecialButtons.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public void setSpecialButtons(@NonNull Map<SpecialButton, SpecialButtonState> specialButtons) {
    mSpecialButtons = specialButtons;
    mSpecialButtonsKeys =
            this.mSpecialButtons.keySet().stream()
                    .map(SpecialButton::getKey)
                    .collect(Collectors.toSet());
  }

  public Set<String> getSpecialButtonsKeys() {
    if (mSpecialButtonsKeys == null) return null;
    return mSpecialButtonsKeys.stream().map(String::new).collect(Collectors.toSet());
  }

  public int getButtonTextColor() {
    return mButtonTextColor;
  }

  public void setButtonTextColor(int buttonTextColor) {
    mButtonTextColor = buttonTextColor;
  }

  public int getButtonActiveTextColor() {
    return mButtonActiveTextColor;
  }

  public void setButtonActiveTextColor(int buttonActiveTextColor) {
    mButtonActiveTextColor = buttonActiveTextColor;
  }

  public int getButtonBackgroundColor() {
    return mButtonBackgroundColor;
  }

  public void setButtonBackgroundColor(int buttonBackgroundColor) {
    mButtonBackgroundColor = buttonBackgroundColor;
  }

  public int getButtonActiveBackgroundColor() {
    return mButtonActiveBackgroundColor;
  }

  public void setButtonActiveBackgroundColor(int buttonActiveBackgroundColor) {
    mButtonActiveBackgroundColor = buttonActiveBackgroundColor;
  }

  public void setButtonTextAllCaps(boolean buttonTextAllCaps) {
    mButtonTextAllCaps = buttonTextAllCaps;
  }

  public int getLongPressTimeout() {
    return mLongPressTimeout;
  }

  public void setLongPressTimeout(int longPressDuration) {
    if (longPressDuration >= MIN_LONG_PRESS_DURATION
            && longPressDuration <= MAX_LONG_PRESS_DURATION) {
      mLongPressTimeout = longPressDuration;
    } else {
      mLongPressTimeout = FALLBACK_LONG_PRESS_DURATION;
    }
  }

  public int getLongPressRepeatDelay() {
    return mLongPressRepeatDelay;
  }

  public void setLongPressRepeatDelay(int longPressRepeatDelay) {
    if (mLongPressRepeatDelay >= MIN_LONG_PRESS__REPEAT_DELAY
            && mLongPressRepeatDelay <= MAX_LONG_PRESS__REPEAT_DELAY) {
      mLongPressRepeatDelay = longPressRepeatDelay;
    } else {
      mLongPressRepeatDelay = DEFAULT_LONG_PRESS_REPEAT_DELAY;
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  public void reload(VirtualKeysInfo extraKeysInfo) {
    if (extraKeysInfo == null) return;

    for (SpecialButtonState state : mSpecialButtons.values()) state.buttons = new ArrayList<>();

    removeAllViews();

    VirtualKeyButton[][] buttons = extraKeysInfo.getMatrix();

    setRowCount(buttons.length);
    setColumnCount(maximumLength(buttons));

    for (int row = 0; row < buttons.length; row++) {
      for (int col = 0; col < buttons[row].length; col++) {
        final VirtualKeyButton buttonInfo = buttons[row][col];

        Button button;
        if (isSpecialButton(buttonInfo)) {
          button = createSpecialButton(buttonInfo.getKey(), true);
          if (button == null) return;
        } else {
          button = new Button(getContext(), null, android.R.attr.buttonBarButtonStyle);
        }

        button.setText(buttonInfo.getDisplay());
        button.setTextColor(mButtonTextColor);
        button.setAllCaps(mButtonTextAllCaps);
        button.setPadding(0, 0, 0, 0);

        button.setOnClickListener(
                view -> {
                  performVirtualKeyButtonHapticFeedback(view, buttonInfo, button);
                  onAnyVirtualKeyButtonClick(view, buttonInfo, button);
                });

        button.setOnTouchListener(
                (view, event) -> {
                  switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                      view.setBackgroundColor(mButtonActiveBackgroundColor);
                      startScheduledExecutors(view, buttonInfo, button);
                      return true;

                    case MotionEvent.ACTION_MOVE:
                      if (buttonInfo.getPopup() != null) {
                        if (mPopupWindow == null && event.getY() < 0) {
                          stopScheduledExecutors();
                          view.setBackgroundColor(mButtonBackgroundColor);
                          showPopup(view, buttonInfo.getPopup());
                        }
                        if (mPopupWindow != null && event.getY() > 0) {
                          view.setBackgroundColor(mButtonActiveBackgroundColor);
                          dismissPopup();
                        }
                      }
                      return true;

                    case MotionEvent.ACTION_CANCEL:
                      view.setBackgroundColor(mButtonBackgroundColor);
                      stopScheduledExecutors();
                      return true;

                    case MotionEvent.ACTION_UP:
                      view.setBackgroundColor(mButtonBackgroundColor);
                      stopScheduledExecutors();
                      if (mLongPressCount == 0 || mPopupWindow != null) {
                        if (mPopupWindow != null) {
                          dismissPopup();
                          if (buttonInfo.getPopup() != null) {
                            onAnyVirtualKeyButtonClick(view, buttonInfo.getPopup(), button);
                          }
                        } else {
                          view.performClick();
                        }
                      }
                      return true;

                    default:
                      return true;
                  }
                });

        LayoutParams param = new GridLayout.LayoutParams();
        param.width = 0;
        param.height = 0;
        param.setMargins(0, 0, 0, 0);
        param.columnSpec = GridLayout.spec(col, GridLayout.FILL, 1.f);
        param.rowSpec = GridLayout.spec(row, GridLayout.FILL, 1.f);
        button.setLayoutParams(param);

        addView(button);
      }
    }
  }

  // 🔥 核心修改：移除 com.rk.settings.Settings 的调用
  private void performVirtualKeyButtonHapticFeedback(
          View view, VirtualKeyButton buttonInfo, Button button) {
    if (mVirtualKeysViewClient != null) {
      if (mVirtualKeysViewClient.performVirtualKeyButtonHapticFeedback(view, buttonInfo, button))
        return;
    }

    try {
      // 直接检查安卓系统的设置，而不是你那个未初始化的库
      if (Settings.System.getInt(
              getContext().getContentResolver(), Settings.System.HAPTIC_FEEDBACK_ENABLED, 0)
              != 0) {

        if (Build.VERSION.SDK_INT >= 28) {
          button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        } else {
          if (Settings.Global.getInt(getContext().getContentResolver(), "zen_mode", 0) != 2) {
            button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
          }
        }
      }
    } catch (Exception e) {
      // 忽略任何设置读取错误，保证不崩溃
    }
  }

  private void onAnyVirtualKeyButtonClick(
          View view, @NonNull VirtualKeyButton buttonInfo, Button button) {
    if (isSpecialButton(buttonInfo)) {
      if (mLongPressCount > 0) return;
      SpecialButtonState state = mSpecialButtons.get(SpecialButton.valueOf(buttonInfo.getKey()));
      if (state == null) return;

      state.setIsActive(!state.isActive);
      if (!state.isActive) state.setIsLocked(false);
    } else {
      onVirtualKeyButtonClick(view, buttonInfo, button);
    }
  }

  private void onVirtualKeyButtonClick(View view, VirtualKeyButton buttonInfo, Button button) {
    if (mVirtualKeysViewClient != null)
      mVirtualKeysViewClient.onVirtualKeyButtonClick(view, buttonInfo, button);
  }

  private void startScheduledExecutors(View view, VirtualKeyButton buttonInfo, Button button) {
    stopScheduledExecutors();
    mLongPressCount = 0;
    if (mRepetitiveKeys.contains(buttonInfo.getKey())) {
      mScheduledExecutor = Executors.newSingleThreadScheduledExecutor();
      mScheduledExecutor.scheduleWithFixedDelay(
              () -> {
                mLongPressCount++;
                onVirtualKeyButtonClick(view, buttonInfo, button);
              },
              mLongPressTimeout,
              mLongPressRepeatDelay,
              TimeUnit.MILLISECONDS);
    } else if (isSpecialButton(buttonInfo)) {
      SpecialButtonState state = mSpecialButtons.get(SpecialButton.valueOf(buttonInfo.getKey()));
      if (state == null) return;
      if (mHandler == null) mHandler = new Handler(Looper.getMainLooper());
      mSpecialButtonsLongHoldRunnable = new SpecialButtonsLongHoldRunnable(state);
      mHandler.postDelayed(mSpecialButtonsLongHoldRunnable, mLongPressTimeout);
    }
  }

  private void stopScheduledExecutors() {
    if (mScheduledExecutor != null) {
      mScheduledExecutor.shutdownNow();
      mScheduledExecutor = null;
    }

    if (mSpecialButtonsLongHoldRunnable != null && mHandler != null) {
      mHandler.removeCallbacks(mSpecialButtonsLongHoldRunnable);
      mSpecialButtonsLongHoldRunnable = null;
    }
  }

  void showPopup(View view, VirtualKeyButton extraButton) {
    int width = view.getMeasuredWidth();
    int height = view.getMeasuredHeight();
    Button button;
    if (isSpecialButton(extraButton)) {
      button = createSpecialButton(extraButton.getKey(), false);
      if (button == null) return;
    } else {
      button = new Button(getContext(), null, android.R.attr.buttonBarButtonStyle);
      button.setTextColor(mButtonTextColor);
    }
    button.setText(extraButton.getDisplay());
    button.setAllCaps(mButtonTextAllCaps);
    button.setPadding(0, 0, 0, 0);
    button.setMinHeight(0);
    button.setMinWidth(0);
    button.setMinimumWidth(0);
    button.setMinimumHeight(0);
    button.setWidth(width);
    button.setHeight(height);
    button.setBackgroundColor(mButtonActiveBackgroundColor);
    mPopupWindow = new PopupWindow(this);
    mPopupWindow.setWidth(LayoutParams.WRAP_CONTENT);
    mPopupWindow.setHeight(LayoutParams.WRAP_CONTENT);
    mPopupWindow.setContentView(button);
    mPopupWindow.setOutsideTouchable(true);
    mPopupWindow.setFocusable(false);
    mPopupWindow.showAsDropDown(view, 0, -2 * height);
  }

  private void dismissPopup() {
    mPopupWindow.setContentView(null);
    mPopupWindow.dismiss();
    mPopupWindow = null;
  }

  public boolean isSpecialButton(VirtualKeyButton button) {
    return mSpecialButtonsKeys.contains(button.getKey());
  }

  private Button createSpecialButton(String buttonKey, boolean needUpdate) {
    SpecialButtonState state = mSpecialButtons.get(SpecialButton.valueOf(buttonKey));
    if (state == null) return null;
    state.setIsCreated(true);
    Button button = new Button(getContext(), null, android.R.attr.buttonBarButtonStyle);
    button.setTextColor(state.isActive ? mButtonActiveTextColor : mButtonTextColor);
    if (needUpdate) {
      state.buttons.add(button);
    }
    return button;
  }

  static int maximumLength(Object[][] matrix) {
    int m = 0;
    for (Object[] row : matrix) m = Math.max(m, row.length);
    return m;
  }

  @Nullable
  public Boolean readSpecialButton(SpecialButton specialButton, boolean autoSetInActive) {
    SpecialButtonState state = mSpecialButtons.get(specialButton);
    if (state == null) return null;

    if (!state.isCreated || !state.isActive) return false;
    if (autoSetInActive && !state.isLocked) state.setIsActive(false);

    return true;
  }

  private class SpecialButtonsLongHoldRunnable implements Runnable {
    private final SpecialButtonState mState;

    public SpecialButtonsLongHoldRunnable(SpecialButtonState state) {
      mState = state;
    }

    public void run() {
      mState.setIsLocked(!mState.isActive);
      mState.setIsActive(!mState.isActive);
      mLongPressCount++;
    }
  }

  public interface IVirtualKeysView {
    void onVirtualKeyButtonClick(View view, VirtualKeyButton buttonInfo, Button button);

    boolean performVirtualKeyButtonHapticFeedback(
            View view, VirtualKeyButton buttonInfo, Button button);
  }
}