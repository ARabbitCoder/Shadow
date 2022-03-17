package com.tencent.shadow.sample.host.lib;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;

public class UICallback {
    public void bindActivity(Activity baseActivity, Resources resources) {

    }

    public void bindActivity(Activity baseActivity, Resources resources,TestData testData) {

    }


    public Dialog showDialog(String a, Activity activity, boolean b, ViewGroup.LayoutParams c) {
        return new Dialog(activity);
    }

    public static class TestData{
        private String testA;
        private int testb;
        private double testc;
        private ViewGroup.LayoutParams params;

        public String getTestA() {
            return testA;
        }

        public void setTestA(String testA) {
            this.testA = testA;
        }

        public int getTestb() {
            return testb;
        }

        public void setTestb(int testb) {
            this.testb = testb;
        }

        public ViewGroup.LayoutParams getParams() {
            return params;
        }

        public void setParams(ViewGroup.LayoutParams params) {
            this.params = params;
        }

        public double getTestc() {
            return testc;
        }

        public void setTestc(double testc) {
            this.testc = testc;
        }
    }
}
