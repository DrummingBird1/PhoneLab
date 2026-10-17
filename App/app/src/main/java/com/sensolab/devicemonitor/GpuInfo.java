package com.sensolab.devicemonitor;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

/**
 * GPU renderer / vendor / GL version. The only public way to get them is to ask OpenGL,
 * so this spins up a throwaway 1×1 off-screen EGL context, reads the strings and tears it
 * down. Cached after the first successful query (the GPU doesn't change at runtime).
 */
public final class GpuInfo {
    private GpuInfo() {}

    public static final class Result {
        public final String renderer, vendor, version;
        Result(String renderer, String vendor, String version) {
            this.renderer = renderer; this.vendor = vendor; this.version = version;
        }
    }

    private static volatile Result cached;

    @WorkerThread @Nullable
    public static Result query() {
        Result r = cached;
        if (r != null) return r;
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) return null;
        int[] version = new int[2];
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null;
        EGLContext context = EGL14.EGL_NO_CONTEXT;
        EGLSurface surface = EGL14.EGL_NO_SURFACE;
        try {
            int[] configAttrs = {
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_NONE };
            EGLConfig[] configs = new EGLConfig[1];
            int[] num = new int[1];
            if (!EGL14.eglChooseConfig(display, configAttrs, 0, configs, 0, 1, num, 0) || num[0] == 0) return null;
            int[] ctxAttrs = { EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE };
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttrs, 0);
            int[] surfAttrs = { EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE };
            surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfAttrs, 0);
            if (context == EGL14.EGL_NO_CONTEXT || surface == EGL14.EGL_NO_SURFACE) return null;
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return null;
            r = new Result(
                    nonNull(GLES20.glGetString(GLES20.GL_RENDERER)),
                    nonNull(GLES20.glGetString(GLES20.GL_VENDOR)),
                    nonNull(GLES20.glGetString(GLES20.GL_VERSION)));
            cached = r;
            return r;
        } catch (RuntimeException e) {
            return null;
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface);
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context);
            EGL14.eglTerminate(display);
        }
    }

    private static String nonNull(@Nullable String s) { return s == null ? "" : s.trim(); }
}
