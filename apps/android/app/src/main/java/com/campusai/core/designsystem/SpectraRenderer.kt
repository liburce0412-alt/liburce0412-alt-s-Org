package com.campusai.core.designsystem

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.view.MotionEvent
import com.campusai.core.model.RenderQuality
import com.campusai.core.model.SpectraEnvironment
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class SpectraSurfaceView(context: Context) : GLSurfaceView(context) {
    private val spectraRenderer = SpectraGlRenderer()

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(spectraRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun configure(environment: SpectraEnvironment, quality: RenderQuality) {
        queueEvent { spectraRenderer.configure(environment, quality) }
    }

    fun setPointer(x: Float, y: Float) {
        queueEvent { spectraRenderer.setPointer(x / width.coerceAtLeast(1), 1f - y / height.coerceAtLeast(1)) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        setPointer(event.x, event.y)
        return false
    }
}

private class SpectraGlRenderer : GLSurfaceView.Renderer {
    private val vertices: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0)
        }
    private var program = 0
    private var startedAt = SystemClock.elapsedRealtime()
    private var environment = SpectraEnvironment.ORIGINAL
    private var quality = RenderQuality.AUTO
    private var pointerX = .62f
    private var pointerY = .45f
    private var width = 1
    private var height = 1

    fun configure(environment: SpectraEnvironment, quality: RenderQuality) {
        this.environment = environment
        this.quality = quality
    }

    fun setPointer(x: Float, y: Float) {
        pointerX = x.coerceIn(0f, 1f)
        pointerY = y.coerceIn(0f, 1f)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = linkProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        GLES20.glClearColor(.97f, .98f, 1f, 1f)
        startedAt = SystemClock.elapsedRealtime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uResolution"), width.toFloat(), height.toFloat())
        val speed = if (quality == RenderQuality.LOW) .018f else .026f
        val seconds = (SystemClock.elapsedRealtime() - startedAt) / 1000f
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uTime"), seconds * speed)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uPointer"), pointerX, pointerY)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uEnvironment"), environment.ordinal.toFloat())
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
    }

    private fun linkProgram(vertex: String, fragment: String): Int {
        fun shader(type: Int, source: String): Int = GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, source); GLES20.glCompileShader(it)
        }
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, shader(GLES20.GL_VERTEX_SHADER, vertex))
            GLES20.glAttachShader(it, shader(GLES20.GL_FRAGMENT_SHADER, fragment))
            GLES20.glLinkProgram(it)
        }
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            varying vec2 vUv;
            void main() { vUv = aPosition * .5 + .5; gl_Position = vec4(aPosition, 0., 1.); }
        """
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vUv;
            uniform vec2 uResolution;
            uniform vec2 uPointer;
            uniform float uTime;
            uniform float uEnvironment;

            float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453); }
            float noise(vec2 p) {
                vec2 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
                return mix(mix(hash(i),hash(i+vec2(1.,0.)),f.x),mix(hash(i+vec2(0.,1.)),hash(i+vec2(1.)),f.x),f.y);
            }
            float fbm(vec2 p) { float v=0.; float a=.52; for(int i=0;i<5;i++){v+=a*noise(p);p=p*2.03+13.17;a*=.5;}return v; }
            vec3 palette(float t) {
                vec3 cyan=vec3(.086,.773,.863), violet=vec3(.459,.384,.961), warm=vec3(1.,.545,.263), rose=vec3(1.,.475,.725);
                if(uEnvironment<.5) return mix(mix(cyan,violet,smoothstep(.05,.48,t)),mix(warm,rose,smoothstep(.55,1.,t)),smoothstep(.38,.7,t));
                if(uEnvironment<1.5) return mix(vec3(.05,.58,.82),vec3(.38,.48,1.),smoothstep(.1,.9,t));
                if(uEnvironment<2.5) return mix(vec3(.35,.24,.9),vec3(.94,.35,.82),smoothstep(.05,.95,t));
                return mix(vec3(1.,.34,.18),vec3(1.,.66,.25),smoothstep(.05,.95,t));
            }
            void main(){
                vec2 uv=vUv; uv.x*=uResolution.x/uResolution.y;
                vec2 p=uv*1.35; float n=fbm(p+vec2(uTime,-uTime*.72));
                float m=fbm(p*1.7+vec2(-uTime*.55,uTime)+n*.8);
                float pointer=exp(-length(vUv-uPointer)*5.2);
                float field=clamp(.14+n*.55+m*.36+pointer*.16,0.,1.);
                vec3 paper=vec3(.973,.977,.992); vec3 color=palette(field);
                float veil=smoothstep(.12,.96,n*m+pointer*.12)*.56;
                color=mix(paper,color,veil);
                color+=vec3(.04,.055,.08)*(1.-smoothstep(.0,.8,length(vUv-vec2(.5))));
                gl_FragColor=vec4(color,1.);
            }
        """
    }
}
