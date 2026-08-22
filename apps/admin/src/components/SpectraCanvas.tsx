import { useEffect, useRef } from 'react'

export type SpectraEnvironment = 'original' | 'ocean' | 'ultraviolet' | 'ember'

type Props = { environment: SpectraEnvironment; motion: boolean; quality: 'auto' | 'low' | 'high' }

const vertex = `
attribute vec2 a_position;
varying vec2 v_uv;
void main(){v_uv=a_position*.5+.5;gl_Position=vec4(a_position,0.,1.);}`

const fragment = `
precision mediump float;
varying vec2 v_uv;
uniform vec2 u_resolution;
uniform vec2 u_pointer;
uniform float u_time;
uniform float u_environment;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(hash(i),hash(i+vec2(1.,0.)),f.x),mix(hash(i+vec2(0.,1.)),hash(i+vec2(1.)),f.x),f.y);}
float fbm(vec2 p){float v=0.,a=.52;for(int i=0;i<5;i++){v+=a*noise(p);p=p*2.03+13.17;a*=.5;}return v;}
vec3 palette(float t){
  vec3 cyan=vec3(.086,.773,.863),violet=vec3(.459,.384,.961),warm=vec3(1.,.545,.263),rose=vec3(1.,.475,.725);
  if(u_environment<.5)return mix(mix(cyan,violet,smoothstep(.05,.48,t)),mix(warm,rose,smoothstep(.55,1.,t)),smoothstep(.38,.7,t));
  if(u_environment<1.5)return mix(vec3(.05,.58,.82),vec3(.38,.48,1.),smoothstep(.1,.9,t));
  if(u_environment<2.5)return mix(vec3(.35,.24,.9),vec3(.94,.35,.82),smoothstep(.05,.95,t));
  return mix(vec3(1.,.34,.18),vec3(1.,.66,.25),smoothstep(.05,.95,t));
}
void main(){
  vec2 uv=v_uv;uv.x*=u_resolution.x/u_resolution.y;vec2 p=uv*1.3;
  float n=fbm(p+vec2(u_time,-u_time*.72));float m=fbm(p*1.7+vec2(-u_time*.55,u_time)+n*.8);
  float pointer=exp(-length(v_uv-u_pointer)*5.2);float field=clamp(.14+n*.55+m*.36+pointer*.16,0.,1.);
  vec3 paper=vec3(.973,.977,.992);vec3 color=mix(paper,palette(field),smoothstep(.12,.96,n*m+pointer*.12)*.58);
  gl_FragColor=vec4(color,1.);
}`

function compile(gl: WebGLRenderingContext, type: number, source: string) {
  const shader = gl.createShader(type)
  if (!shader) throw new Error('无法创建 SPECTRA shader')
  gl.shaderSource(shader, source)
  gl.compileShader(shader)
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(shader) ?? 'shader 编译失败')
  return shader
}

export function SpectraCanvas({ environment, motion, quality }: Props) {
  const ref = useRef<HTMLCanvasElement>(null)

  useEffect(() => {
    const canvas = ref.current
    if (!canvas || !motion) return
    const gl = canvas.getContext('webgl2', { alpha: false, antialias: quality !== 'low', powerPreference: quality === 'high' ? 'high-performance' : 'default' })
      ?? canvas.getContext('webgl', { alpha: false, antialias: quality !== 'low' })
    if (!gl) { document.documentElement.dataset.spectraFallback = 'true'; return }

    const program = gl.createProgram()
    if (!program) return
    try {
      gl.attachShader(program, compile(gl, gl.VERTEX_SHADER, vertex))
      gl.attachShader(program, compile(gl, gl.FRAGMENT_SHADER, fragment))
      gl.linkProgram(program)
      if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(program) ?? 'shader 链接失败')
    } catch (error) {
      console.error(error)
      document.documentElement.dataset.spectraFallback = 'true'
      return
    }
    document.documentElement.dataset.spectraFallback = 'false'
    gl.useProgram(program)
    const buffer = gl.createBuffer()
    gl.bindBuffer(gl.ARRAY_BUFFER, buffer)
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1,-1,1,-1,-1,1,1,1]), gl.STATIC_DRAW)
    const position = gl.getAttribLocation(program, 'a_position')
    gl.enableVertexAttribArray(position)
    gl.vertexAttribPointer(position, 2, gl.FLOAT, false, 0, 0)
    const resolution = gl.getUniformLocation(program, 'u_resolution')
    const pointer = gl.getUniformLocation(program, 'u_pointer')
    const time = gl.getUniformLocation(program, 'u_time')
    const env = gl.getUniformLocation(program, 'u_environment')
    const pointerState = { x: .62, y: .45 }
    const envIndex = ['original','ocean','ultraviolet','ember'].indexOf(environment)
    let raf = 0
    let lastFrame = 0
    const fps = quality === 'low' ? 20 : 30
    const frameDuration = 1000 / fps

    const resize = () => {
      const scale = Math.min(window.devicePixelRatio || 1, quality === 'high' ? 2 : quality === 'low' ? 1 : 1.5)
      const width = Math.max(1, Math.floor(canvas.clientWidth * scale))
      const height = Math.max(1, Math.floor(canvas.clientHeight * scale))
      if (canvas.width !== width || canvas.height !== height) { canvas.width = width; canvas.height = height; gl.viewport(0,0,width,height) }
    }
    const draw = (now: number) => {
      raf = requestAnimationFrame(draw)
      if (document.hidden || now - lastFrame < frameDuration) return
      lastFrame = now
      resize()
      gl.uniform2f(resolution, canvas.width, canvas.height)
      gl.uniform2f(pointer, pointerState.x, pointerState.y)
      gl.uniform1f(time, now * .000026)
      gl.uniform1f(env, envIndex)
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4)
    }
    const onPointer = (event: PointerEvent) => { pointerState.x = event.clientX / innerWidth; pointerState.y = 1 - event.clientY / innerHeight }
    const onLost = (event: Event) => { event.preventDefault(); document.documentElement.dataset.spectraFallback = 'true'; cancelAnimationFrame(raf) }
    window.addEventListener('pointermove', onPointer, { passive: true })
    canvas.addEventListener('webglcontextlost', onLost)
    raf = requestAnimationFrame(draw)
    return () => { cancelAnimationFrame(raf); window.removeEventListener('pointermove', onPointer); canvas.removeEventListener('webglcontextlost', onLost); gl.deleteProgram(program); gl.deleteBuffer(buffer) }
  }, [environment, motion, quality])

  if (!motion) return null
  return <canvas ref={ref} className="spectra-canvas" aria-hidden="true" />
}
