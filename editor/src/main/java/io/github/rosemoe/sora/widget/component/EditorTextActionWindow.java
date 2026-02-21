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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;

import io.github.rosemoe.sora.R;
import io.github.rosemoe.sora.event.ColorSchemeUpdateEvent;
import io.github.rosemoe.sora.event.DragSelectStopEvent;
import io.github.rosemoe.sora.event.EditorFocusChangeEvent;
import io.github.rosemoe.sora.event.EditorReleaseEvent;
import io.github.rosemoe.sora.event.EventManager;
import io.github.rosemoe.sora.event.HandleStateChangeEvent;
import io.github.rosemoe.sora.event.InterceptTarget;
import io.github.rosemoe.sora.event.LongPressEvent;
import io.github.rosemoe.sora.event.ScrollEvent;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.EditorTouchEventHandler;
import io.github.rosemoe.sora.widget.base.EditorPopupWindow;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * This window will show when selecting text to present text actions.
 *
 * @author Rosemoe
 */
public class EditorTextActionWindow extends EditorPopupWindow implements View.OnClickListener, EditorBuiltinComponent {
    private final static String TAG = "EditorTextActionWindow";
    private final static long DELAY = 200;
    private final static long CHECK_FOR_DISMISS_INTERVAL = 100;
    private final CodeEditor editor;
    private final ImageButton selectAllBtn;
    private final ImageButton pasteBtn;
    private final ImageButton copyBtn;
    private final ImageButton cutBtn;
    private final ImageButton longSelectBtn;
    private final View rootView;
    private final LinearLayout panelButtonContainer;
    private final EditorTouchEventHandler handler;
    private final EventManager eventManager;
    private long lastScroll;
    private int lastPosition;
    private int lastCause;
    private boolean enabled = true;

    private final List<TextActionItem> registeredActionItems = new ArrayList<>();

    /**
     * Create a panel for the given editor
     *
     * @param editor Target editor
     */
    public EditorTextActionWindow(CodeEditor editor) {
        super(editor, FEATURE_SHOW_OUTSIDE_VIEW_ALLOWED);
        this.editor = editor;
        handler = editor.getEventHandler();
        eventManager = editor.createSubEventManager();

        // Since popup window does provide decor view, we have to pass null to this method
        @SuppressLint("InflateParams")
        View root = this.rootView = LayoutInflater.from(editor.getContext()).inflate(R.layout.text_compose_panel, null);
        this.panelButtonContainer = root.findViewById(R.id.panel_button_container);
        selectAllBtn = root.findViewById(R.id.panel_btn_select_all);
        cutBtn = root.findViewById(R.id.panel_btn_cut);
        copyBtn = root.findViewById(R.id.panel_btn_copy);
        longSelectBtn = root.findViewById(R.id.panel_btn_long_select);
        pasteBtn = root.findViewById(R.id.panel_btn_paste);

        selectAllBtn.setOnClickListener(this);
        cutBtn.setOnClickListener(this);
        copyBtn.setOnClickListener(this);
        pasteBtn.setOnClickListener(this);
        longSelectBtn.setOnClickListener(this);

        applyColorScheme();
        setContentView(root);
        setSize(0, (int) (this.editor.getDpUnit() * 48));
        getPopup().setAnimationStyle(R.style.text_action_popup_animation);

        subscribeEvents();
    }

    protected void applyColorFilter(ImageButton btn, int color) {
        var drawable = btn.getDrawable();
        if (drawable == null) {
            return;
        }
        btn.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP));
    }

    protected void applyColorScheme() {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(5 * editor.getDpUnit());
        gd.setColor(editor.getColorScheme().getColor(EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND));
        rootView.setBackground(gd);
        int color = editor.getColorScheme().getColor(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR);
        applyColorFilter(selectAllBtn, color);
        applyColorFilter(cutBtn, color);
        applyColorFilter(copyBtn, color);
        applyColorFilter(pasteBtn, color);
        applyColorFilter(longSelectBtn, color);

        // Registered action buttons
        for (TextActionItem actionItem : registeredActionItems) {
            ImageButton imageButton = actionItem.getActionButton();
            if (imageButton != null) applyColorFilter(imageButton, color);
        }
    }

    protected void subscribeEvents() {
        eventManager.subscribeAlways(SelectionChangeEvent.class, this::onSelectionChange);
        eventManager.subscribeAlways(ScrollEvent.class, this::onEditorScroll);
        eventManager.subscribeAlways(HandleStateChangeEvent.class, this::onHandleStateChange);
        eventManager.subscribeAlways(LongPressEvent.class, this::onEditorLongPress);
        eventManager.subscribeAlways(EditorFocusChangeEvent.class, this::onEditorFocusChange);
        eventManager.subscribeAlways(EditorReleaseEvent.class, this::onEditorRelease);
        eventManager.subscribeAlways(ColorSchemeUpdateEvent.class, this::onEditorColorChange);
        eventManager.subscribeAlways(DragSelectStopEvent.class, this::onDragSelectingStop);
    }

    protected void onEditorColorChange(@NonNull ColorSchemeUpdateEvent event) {
        applyColorScheme();
    }

    protected void onEditorFocusChange(@NonNull EditorFocusChangeEvent event) {
        if (!event.isGainFocus()) {
            dismiss();
        }
    }

    protected void onDragSelectingStop(@NonNull DragSelectStopEvent event) {
        displayWindow();
    }

    protected void onEditorRelease(@NonNull EditorReleaseEvent event) {
        setEnabled(false);
    }

    protected void onEditorLongPress(@NonNull LongPressEvent event) {
        if (editor.getCursor().isSelected() && lastCause == SelectionChangeEvent.CAUSE_SEARCH) {
            var idx = event.getIndex();
            if (idx >= editor.getCursor().getLeft() && idx <= editor.getCursor().getRight()) {
                lastCause = 0;
                displayWindow();
            }
            event.intercept(InterceptTarget.TARGET_EDITOR);
        }
    }

    protected void onEditorScroll(@NonNull ScrollEvent event) {
        var last = lastScroll;
        lastScroll = System.currentTimeMillis();
        if (lastScroll - last < DELAY && lastCause != SelectionChangeEvent.CAUSE_SEARCH) {
            postDisplay();
        }
    }

    protected void onHandleStateChange(@NonNull HandleStateChangeEvent event) {
        if (event.isHeld()) {
            postDisplay();
        }
        if (!event.getEditor().getCursor().isSelected()
                && event.getHandleType() == HandleStateChangeEvent.HANDLE_TYPE_INSERT
                && !event.isHeld()) {
            displayWindow();
            // Also, post to hide the window on handle disappearance
            editor.postDelayedInLifecycle(new Runnable() {
                @Override
                public void run() {
                    if (!editor.getEventHandler().shouldDrawInsertHandle()
                            && !editor.getCursor().isSelected()) {
                        dismiss();
                    } else if (!editor.getCursor().isSelected()) {
                        editor.postDelayedInLifecycle(this, CHECK_FOR_DISMISS_INTERVAL);
                    }
                }
            }, CHECK_FOR_DISMISS_INTERVAL);
        }
    }

    protected void onSelectionChange(@NonNull SelectionChangeEvent event) {
        if (handler.hasAnyHeldHandle() || event.getCause() == SelectionChangeEvent.CAUSE_DEAD_KEYS) {
            return;
        }
        if (handler.isDragSelecting()) {
            dismiss();
            return;
        }
        lastCause = event.getCause();
        if (event.isSelected() || event.getCause() == SelectionChangeEvent.CAUSE_LONG_PRESS && editor.getText().length() == 0) {
            // Always post show. See #193
            if (event.getCause() != SelectionChangeEvent.CAUSE_SEARCH) {
                editor.postInLifecycle(this::displayWindow);
            } else {
                dismiss();
            }
            lastPosition = -1;
        } else {
            var show = false;
            if (event.getCause() == SelectionChangeEvent.CAUSE_TAP && event.getLeft().index == lastPosition && !isShowing() && !editor.getText().isInBatchEdit() && editor.isEditable()) {
                editor.postInLifecycle(this::displayWindow);
                show = true;
            } else {
                dismiss();
            }
            if (event.getCause() == SelectionChangeEvent.CAUSE_TAP && !show) {
                lastPosition = event.getLeft().index;
            } else {
                lastPosition = -1;
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        eventManager.setEnabled(enabled);
        if (!enabled) {
            dismiss();
        }
    }

    /**
     * Get the view root of the panel.
     * <p>
     * Root view is {@link android.widget.LinearLayout}
     * Inside is a {@link android.widget.HorizontalScrollView}
     *
     * @see R.id#panel_root
     * @see R.id#panel_hv
     * @see R.id#panel_btn_select_all
     * @see R.id#panel_btn_copy
     * @see R.id#panel_btn_cut
     * @see R.id#panel_btn_paste
     */
    public ViewGroup getView() {
        return (ViewGroup) getPopup().getContentView();
    }

    private void postDisplay() {
        if (!isShowing()) {
            return;
        }
        dismiss();
        if (!editor.getCursor().isSelected()) {
            return;
        }
        editor.postDelayedInLifecycle(new Runnable() {
            @Override
            public void run() {
                if (!handler.hasAnyHeldHandle() && !editor.getSnippetController().isInSnippet() && System.currentTimeMillis() - lastScroll > DELAY
                        && editor.getScroller().isFinished()) {
                    displayWindow();
                } else {
                    editor.postDelayedInLifecycle(this, DELAY);
                }
            }
        }, DELAY);
    }

    private int selectTop(@NonNull RectF rect) {
        var rowHeight = editor.getRowHeight();
        if (rect.top - rowHeight * 3 / 2F > getHeight()) {
            return (int) (rect.top - rowHeight * 3 / 2 - getHeight());
        } else {
            return (int) (rect.bottom + rowHeight / 2);
        }
    }

    public void displayWindow() {
        updateBtnState();
        int top;
        var cursor = editor.getCursor();
        if (cursor.isSelected()) {
            var leftRect = editor.getLeftHandleDescriptor().position;
            var rightRect = editor.getRightHandleDescriptor().position;
            var top1 = selectTop(leftRect);
            var top2 = selectTop(rightRect);
            top = Math.min(top1, top2);
        } else {
            top = selectTop(editor.getInsertHandleDescriptor().position);
        }
        top = Math.max(0, Math.min(top, editor.getHeight() - getHeight() - 5));
        float handleLeftX = editor.getOffset(editor.getCursor().getLeftLine(), editor.getCursor().getLeftColumn());
        float handleRightX = editor.getOffset(editor.getCursor().getRightLine(), editor.getCursor().getRightColumn());
        int panelX = (int) ((handleLeftX + handleRightX) / 2f - panelButtonContainer.getMeasuredWidth() / 2f);
        setLocationAbsolutely(panelX, top);
        show();
    }

    /**
     * Update the state of paste button
     */
    private void updateBtnState() {
        pasteBtn.setEnabled(editor.hasClip());
        copyBtn.setVisibility(editor.getCursor().isSelected() ? View.VISIBLE : View.GONE);
        pasteBtn.setVisibility(editor.isEditable() ? View.VISIBLE : View.GONE);
        cutBtn.setVisibility((editor.getCursor().isSelected() && editor.isEditable()) ? View.VISIBLE : View.GONE);
        longSelectBtn.setVisibility((!editor.getCursor().isSelected() && editor.isEditable()) ? View.VISIBLE : View.GONE);

        for (TextActionItem actionItem : registeredActionItems) {
            ImageButton imageButton = actionItem.getActionButton();
            if (imageButton != null) {
                imageButton.setVisibility(actionItem.getShouldShow().invoke(editor) ? View.VISIBLE : View.GONE);
            }
        }

        final int NON_SCROLL_ITEM_COUNT = 7;
        int widthSpec = View.MeasureSpec.makeMeasureSpec((int) (editor.getDpUnit() * (45 * NON_SCROLL_ITEM_COUNT + 5)), View.MeasureSpec.AT_MOST);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);

        panelButtonContainer.measure(widthSpec, heightSpec);
        setSize(panelButtonContainer.getMeasuredWidth(), getHeight());
    }

    @Override
    public void show() {
        if (!enabled || editor.getSnippetController().isInSnippet() || !editor.hasFocus() || editor.isInMouseMode()) {
            return;
        }
        super.show();
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.panel_btn_select_all) {
            editor.selectAll();
            return;
        } else if (id == R.id.panel_btn_cut) {
            if (editor.getCursor().isSelected()) {
                editor.cutText();
            }
        } else if (id == R.id.panel_btn_paste) {
            editor.pasteText();
            editor.setSelection(editor.getCursor().getRightLine(), editor.getCursor().getRightColumn());
        } else if (id == R.id.panel_btn_copy) {
            editor.copyText();
            editor.setSelection(editor.getCursor().getRightLine(), editor.getCursor().getRightColumn());
        } else if (id == R.id.panel_btn_long_select) {
            editor.beginLongSelect();
        }
        dismiss();
    }

    /**
     * Register an action button in the text action window.
     *
     * @param item The text action item instance to register.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void registerTextAction(@NonNull TextActionItem item) {
        if (registeredActionItems.contains(item)) return;

        final Context context = editor.getContext();

        ImageButton btn = new ImageButton(context);
        btn.setImageResource(item.getIconRes());
        btn.setContentDescription(context.getString(item.getTitleRes()));

        final int btnSize = (int) (editor.getDpUnit() * 45);
        btn.setLayoutParams(new LinearLayout.LayoutParams(btnSize, btnSize));

        // Same background as default action items
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        btn.setBackgroundResource(value.resourceId);

        panelButtonContainer.addView(btn);

        item.setActionButton(btn);
        registeredActionItems.add(item);

        btn.setOnClickListener(v -> {
            try {
                item.getOnClick().invoke(editor);
            } catch (Exception ex) {
                Log.w(TAG, "Failed to execute action", ex);
            }
            dismiss();
        });

        applyColorScheme();
        updateBtnState();
    }

    /**
     * Unregister an action button in the text action window.
     *
     * @param item The text action item instance to unregister.
     */
    public void unregisterTextAction(@NonNull TextActionItem item) {
        registeredActionItems.remove(item);
        item.setActionButton(null);
        updateBtnState();
    }
}

