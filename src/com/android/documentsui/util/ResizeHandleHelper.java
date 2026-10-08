package com.android.documentsui.util;


import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

public class ResizeHandleHelper {
    private static final int MIN_WIDTH = 150;  
    private static final int MAX_WIDTH = 300;  

    private View resizeHandle;
    private View resizableView;
    private View resizableView2;
    private View navButtons;
    private ViewGroup.LayoutParams layoutParams;
    private ViewGroup.LayoutParams layoutParams2;


    private float startX;
    private int startWidth;

    public ResizeHandleHelper(View resizeHandle, View resizableView, View resizableView2,View navButtons) {
        this.resizeHandle = resizeHandle;
        this.resizableView = resizableView;
        this.resizableView2 = resizableView2;
        this.navButtons = navButtons;
        this.layoutParams = resizableView.getLayoutParams();
        this.layoutParams2 = resizableView2.getLayoutParams();


        setupResizeHandle();
    }

    private void setupResizeHandle() {
        resizeHandle.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startWidth = layoutParams.width;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - startX;
                        int newWidth = startWidth + (int) deltaX;
                        if (newWidth >= MIN_WIDTH && newWidth <= MAX_WIDTH) {
                            layoutParams.width = newWidth;
                            layoutParams2.width = newWidth;
                            resizableView.setLayoutParams(layoutParams);
                            resizableView2.setLayoutParams(layoutParams2);

                            if (navButtons != null) {
                                ViewGroup.MarginLayoutParams navLp =
                                        (ViewGroup.MarginLayoutParams) navButtons.getLayoutParams();

                                navLp.setMarginStart(newWidth);
                                navButtons.setLayoutParams(navLp);
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                }
                return false;
            }
        });
    }
}
