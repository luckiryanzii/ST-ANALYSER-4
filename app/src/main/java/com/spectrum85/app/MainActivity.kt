package com.spectrum85.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.RectF
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Shader
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.media.MediaMetadata
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.media.audiofx.Visualizer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.*
import kotlin.math.*

class MainActivity : Activity() {
 private lateinit var display: SpectrumView
 private var visualizer: Visualizer? = null
 private val main = Handler(Looper.getMainLooper())
 private var bands = 10
 private var theme = 0
 private var sensitivity = 1.0f
 private var bassBalance = 0.62f
 private var glow = 2
 private var marqueeMode = 0
 private var trackText = ""
 private var trackChangedAt = 0L
 private var isMediaPlaying = false
 private var immersive = true
 private var decay = 0.035f
 private var peakHold = true
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val prefs = getSharedPreferences("spectrum85", MODE_PRIVATE)
  sensitivity = prefs.getFloat("gain", 1.0f)
  bassBalance = prefs.getFloat("bass", 0.62f)
  glow = prefs.getInt("glow", 2)
  marqueeMode = prefs.getInt("marquee", 0)
  theme = prefs.getInt("theme", 0)
  bands = prefs.getInt("bands", 10).takeIf { it in listOf(10,20,31) } ?: 10
  immersive = prefs.getBoolean("immersive", true)
  applyFullscreen()
  window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  display = SpectrumView(); setContentView(display)

  // Android's Visualizer API requires RECORD_AUDIO authorisation even when
  // analysing the OUTPUT MIX. This app does not open a microphone recorder.
  if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
   requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
  else startAudio()
 }
 override fun onRequestPermissionsResult(requestCode:Int, permissions:Array<out String>, grantResults:IntArray) {
  super.onRequestPermissionsResult(requestCode,permissions,grantResults)
  if(requestCode==1 && grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED) startAudio()
  else Toast.makeText(this,"Android audio-analysis permission is required for device playback visualisation. The microphone is never used.",Toast.LENGTH_LONG).show()
 }
 private fun applyFullscreen() {
  val controller = window.decorView.windowInsetsController ?: return
  if (immersive) controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
  else controller.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
  controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
 }
 private fun saveSettings() {
  getSharedPreferences("spectrum85", MODE_PRIVATE).edit()
   .putFloat("gain", sensitivity).putFloat("bass", bassBalance)
   .putInt("glow", glow).putInt("theme",theme).putInt("marquee",marqueeMode).putInt("bands",bands).putBoolean("immersive", immersive).apply()
 }
 private fun showMenu() {
  val scroll = ScrollView(this)
  val layout = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
   setPadding(32, 16, 32, 16)
  }
  scroll.addView(layout)
  fun slider(title: String, progress: Int, onChange: (Int) -> Unit) {
   val label = TextView(this).apply { text = title; textSize = 16f; setPadding(0, 18, 0, 4) }
   layout.addView(label)
   val seek = SeekBar(this).apply { max = 100; setProgress(progress) }
   layout.addView(seek)
   seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
    override fun onProgressChanged(bar: SeekBar?, value: Int, user: Boolean) {
     if (user) { onChange(value); saveSettings() }
    }
    override fun onStartTrackingTouch(bar: SeekBar?) {}
    override fun onStopTrackingTouch(bar: SeekBar?) {}
   })
  }
  fun choice(title: String, options: Array<String>, current: Int, onPick: (Int) -> Unit) {
   layout.addView(TextView(this).apply { text = title; textSize = 16f; setPadding(0, 18, 0, 4) })
   val spinner = Spinner(this)
   spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
   spinner.setSelection(current.coerceIn(0, options.lastIndex))
   layout.addView(spinner)
   spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
     onPick(position); saveSettings()
    }
   }
  }
  slider("Master sensitivity", ((sensitivity - 0.35f) / 1.45f * 100).roundToInt().coerceIn(0, 100)) {
   sensitivity = 0.35f + it / 100f * 1.45f
  }
  slider("Bass balance", (bassBalance * 100).roundToInt().coerceIn(0, 100)) {
   bassBalance = it / 100f
  }
  choice("Illumination", arrayOf("Off", "Subtle", "Authentic", "Heavy"), glow) { glow = it }
  choice("Track title", arrayOf("Enter briskly, then hold", "Continuous scroll", "Static", "Hidden"), marqueeMode) { marqueeMode=it }
  layout.addView(Button(this).apply { text="Enable music controls / track info"; setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } })
  choice("Colour", arrayOf("ST Classic: yellow / orange / red", "Turquoise / ice", "Amber", "Full rainbow", "Fire: gold / orange / red", "Violet / pink / cyan", "Emerald / electric blue"), theme) {
   theme = it; display.invalidate()
  }
  choice("Frequency bands", arrayOf("10", "20", "31"), listOf(10,20,31).indexOf(bands)) {
   bands = listOf(10,20,31)[it]; display.reset()
  }
  layout.addView(TextView(this).apply {
   text = "Audio source: device playback only (microphone disabled)"
   textSize = 14f
   setPadding(0, 16, 0, 4)
  })
  choice("Bar decay", arrayOf("Slow", "Normal", "Fast"),
   when(decay) { 0.018f -> 0; 0.065f -> 2; else -> 1 }) {
   decay = listOf(0.018f, 0.035f, 0.065f)[it]
  }
  layout.addView(CheckBox(this).apply {
   text = "Floating peak indicators"
   isChecked = peakHold
   setOnCheckedChangeListener { _, checked -> peakHold = checked }
  })
  layout.addView(CheckBox(this).apply {
   text = "Immersive full screen (swipe from edge for system controls)"
   isChecked = immersive
   setOnCheckedChangeListener { _, checked ->
    immersive = checked; saveSettings(); applyFullscreen()
   }
  })
  val dialog = android.app.AlertDialog.Builder(this)
   .setTitle("ST-ANALYSER  •  SETTINGS")
   .setView(scroll)
   .setPositiveButton("Done", null)
   .setNeutralButton("Exit app") { _, _ -> finish() }
   .create()
  dialog.show()
 }
 private fun startAudio() {
  stopAudio()
  if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
  try {
   // Session 0 is Android's output mix. Never create an AudioRecord or use MIC.
   val v = Visualizer(0)
   v.captureSize = Visualizer.getCaptureSizeRange()[1]
   v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, rate: Int) {}
    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {
     if (fft != null && ::display.isInitialized) {
      // Visualizer's callback buffer is only valid during this callback.
      val snapshot = fft.clone()
      val sampleRateHz = if (rate > 100000) rate / 1000 else rate
      display.post { display.consumeFft(snapshot, sampleRateHz.coerceAtLeast(8000)) }
     }
    }
   }, Visualizer.getMaxCaptureRate() / 2, false, true)
   v.enabled = true
   visualizer = v
  } catch (e: Exception) {
   // Fail safely: NEVER fall back to microphone capture.
   Toast.makeText(this, "Device playback analysis unavailable on this device: ${e.message}", Toast.LENGTH_LONG).show()
  }
 }
 private fun stopAudio() {
  val v = visualizer
  visualizer = null
  if (v != null) try {
   v.enabled = false
   v.release()
  } catch (_: Exception) {}
 }
 override fun onPause(){super.onPause();stopAudio()}
 override fun onResume(){super.onResume();if(::display.isInitialized && checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)startAudio()}
 override fun onDestroy(){stopAudio();super.onDestroy()}
 private fun activeSession(): android.media.session.MediaController? {
  return try {
   val mgr=getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
   val component=ComponentName(this, SpectrumNotificationListener::class.java)
   val sessions=mgr.getActiveSessions(component)
   sessions.firstOrNull { it.playbackState?.state==PlaybackState.STATE_PLAYING }
    ?: sessions.firstOrNull()
  } catch (_:Exception) { null }
 }
 private fun transport(action:Int) {
  val controls=activeSession()?.transportControls
  if(controls==null) {
   Toast.makeText(this,"Enable notification access in Settings for playback controls",Toast.LENGTH_SHORT).show()
   startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));return
  }
  when(action) {
   0 -> controls.skipToPrevious()
   1 -> {
    if(activeSession()?.playbackState?.state==PlaybackState.STATE_PLAYING) {
     controls.pause();isMediaPlaying=false
    } else {
     controls.play();isMediaPlaying=true
    }
    display.invalidate()
   }
   2 -> controls.skipToNext()
  }
 }
 private fun updateTrack() {
  val session=activeSession()
  isMediaPlaying=session?.playbackState?.state==PlaybackState.STATE_PLAYING
  val meta=session?.metadata
  val title=meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
  val artist=meta?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
  val next=listOf(artist,title).filter { it.isNotBlank() }.joinToString("  -  ").uppercase(java.util.Locale.ROOT)
  if(next!=trackText) {trackText=next;trackChangedAt=android.os.SystemClock.uptimeMillis()}
 }

 private data class LedMatrix(val columns:Int,val rows:Int,val pitch:Float,val lit:BooleanArray) {
  val width:Float get() = columns*pitch
  val height:Float get() = rows*pitch
 }
 inner class SpectrumView:View(this) {
  private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
  private var levels=FloatArray(31);private var peaks=FloatArray(31);private var holds=IntArray(31)
  private var latest=FloatArray(256);private var last=System.nanoTime()
  private val trails=Array(31){FloatArray(36)}
  private val menuBounds=RectF()
  private val buttons=Array(3){RectF()}
  private val glowPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { setLayerTypeDummy() }
  private fun Paint.setLayerTypeDummy() { maskFilter=BlurMaskFilter(16f,BlurMaskFilter.Blur.NORMAL) }
  private val titlePaint=Paint(Paint.ANTI_ALIAS_FLAG)
  private var lastMetadataCheck=0L
  // We rasterise a bold italic sans-serif silhouette into a grid of real LED squares.
  // That bitmap is ONLY a template for illuminated emitters, never a background.
  // The separate thin track display uses its own less heavy sans-serif template.
  private val matrixCache=LinkedHashMap<String,LedMatrix>()
  private fun matrix(text:String,fontPx:Float,pitch:Float,bold:Boolean,italic:Boolean):LedMatrix {
   val key="$text|${fontPx.roundToInt()}|${(pitch*10).roundToInt()}|$bold|$italic"
   matrixCache[key]?.let{return it}
   val font=Typeface.create(if(italic) "sans-serif-condensed" else "sans-serif",
    if(bold && italic) Typeface.BOLD_ITALIC else if(bold) Typeface.BOLD else Typeface.NORMAL)
   val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color=Color.WHITE;typeface=font;textSize=fontPx;isSubpixelText=true
   }
   val fm=p.fontMetrics
   val pad=ceil(pitch*2).toInt().coerceAtLeast(4)
   val bitmapWidth=(ceil(p.measureText(text)).toInt()+pad*2).coerceAtLeast(1)
   val bitmapHeight=(ceil(fm.descent-fm.ascent).toInt()+pad*2).coerceAtLeast(1)
   val bitmap=Bitmap.createBitmap(bitmapWidth,bitmapHeight,Bitmap.Config.ARGB_8888)
   Canvas(bitmap).drawText(text,pad.toFloat(),pad-fm.ascent,p)
   val argb=IntArray(bitmapWidth*bitmapHeight)
   bitmap.getPixels(argb,0,bitmapWidth,0,0,bitmapWidth,bitmapHeight)
   bitmap.recycle()
   val cols=ceil(bitmapWidth/pitch).toInt()
   val rows=ceil(bitmapHeight/pitch).toInt()
   val on=BooleanArray(cols*rows)
   for(row in 0 until rows)for(col in 0 until cols) {
    val x=((col+.5f)*pitch).toInt().coerceIn(0,bitmapWidth-1)
    val y=((row+.5f)*pitch).toInt().coerceIn(0,bitmapHeight-1)
    // Sampling a small cross prevents thin strokes from dropping out between LEDs.
    var alpha=0
    val radius=max(1,(pitch*.22f).roundToInt())
    for(dy in -radius..radius)for(dx in -radius..radius) {
     if(abs(dx)+abs(dy)>radius+1)continue
     val sx=(x+dx).coerceIn(0,bitmapWidth-1)
     val sy=(y+dy).coerceIn(0,bitmapHeight-1)
     alpha=max(alpha,Color.alpha(argb[sy*bitmapWidth+sx]))
    }
    on[row*cols+col]=alpha>=105
   }
   val result=LedMatrix(cols,rows,pitch,on)
   if(matrixCache.size>12)matrixCache.clear()
   matrixCache[key]=result
   return result
  }
  private fun accent(index:Int=0, total:Int=1): Int = when(theme) {
   1 -> Color.rgb(61,237,249) // ice cyan
   2 -> Color.rgb(255,187,46) // amber
   3 -> Color.HSVToColor(floatArrayOf((index*330f/max(1,total)+195f)%360f, .95f, 1f)) // rainbow
   4 -> Color.rgb(255,126,32) // fire
   5 -> Color.rgb(242,94,241) // violet
   6 -> Color.rgb(59,209,253) // electric blue
   else -> Color.rgb(255,184,41) // classic amber
  }
  private val ledCore=Paint(Paint.ANTI_ALIAS_FLAG)
  private val ledHalo=Paint(Paint.ANTI_ALIAS_FLAG)
  private fun ledMatrix(canvas:Canvas,grid:LedMatrix,x:Float,y:Float,tint:Int,
                        dotRatio:Float=.77f,bloom:Float=1f,rainbow:Boolean=false) {
   val pitch=grid.pitch
   val emitter=(pitch*dotRatio).coerceAtLeast(.8f)
   val blur=BlurMaskFilter(pitch*1.6f*bloom,BlurMaskFilter.Blur.NORMAL)
   ledHalo.maskFilter=blur
   val colours=IntArray(grid.columns){col ->
    if(rainbow && theme==3)
     Color.HSVToColor(floatArrayOf((col*300f/grid.columns+185f)%360f,.95f,1f))
    else tint
   }
   canvas.saveLayer(x-pitch*5,y-pitch*5,x+grid.width+pitch*5,y+grid.height+pitch*5,null)
   for(row in 0 until grid.rows)for(col in 0 until grid.columns) {
    if(!grid.lit[row*grid.columns+col])continue
    val c=colours[col]
    ledCore.color=c
    ledHalo.color=Color.argb((115*bloom).roundToInt().coerceIn(0,210),Color.red(c),Color.green(c),Color.blue(c))
    val px=x+col*pitch;val py=y+row*pitch
    canvas.drawRect(px-pitch*.28f,py-pitch*.28f,px+emitter+pitch*.28f,py+emitter+pitch*.28f,ledHalo)
    canvas.drawRect(px,py,px+emitter,py+emitter,ledCore)
   }
   canvas.restore()
   ledHalo.maskFilter=null
  }
  private fun pixelReflection(canvas:Canvas,x:Float,y:Float,width:Float,height:Float,color:Int) {
   if(glow==0)return
   val reflection=Paint(Paint.ANTI_ALIAS_FLAG).apply {
    shader=LinearGradient(x,y,x,y+height,
     intArrayOf(Color.argb(83,Color.red(color),Color.green(color),Color.blue(color)),
       Color.argb(20,Color.red(color),Color.green(color),Color.blue(color)),Color.TRANSPARENT),
     null,Shader.TileMode.CLAMP)
   }
   canvas.drawRoundRect(x,y,x+width,y+height,width*.05f,width*.05f,reflection)
  }
  // Single left arrow | circled PLAY when stopped, PAUSE when playing | single right arrow.
  // All three are genuine pixel-grid icons; no Android system icons or bitmaps.
  private fun ledTransport(canvas:Canvas,index:Int,cx:Float,cy:Float,size:Float,
                           playing:Boolean,tint:Int) {
   val grid=21;val pitch=size/grid
   val x0=cx-size*.5f;val y0=cy-size*.5f
   val pixels=BooleanArray(grid*grid)
   for(y in 0 until grid)for(x in 0 until grid) {
    val dy=abs(y-10)
    pixels[y*grid+x]=when(index) {
     0 -> (x in 2..14 && dy<=(x-2)*.63f) || (x in 13..18 && y in 8..12)
     2 -> (x in 6..18 && dy<=(18-x)*.63f) || (x in 2..7 && y in 8..12)
     else -> if(playing) ((x in 5..7 || x in 13..15) && y in 3..17)
      else {
       val radius=hypot((x-10).toFloat(),(y-10).toFloat())
       val ring=abs(radius-8f)<=.85f
       val triangle=x in 8..14 && dy<=(14-x)*.82f
       ring || triangle
      }
    }
   }
   ledMatrix(canvas,LedMatrix(grid,grid,pitch,pixels),x0,y0,tint,.78f,1.15f)
   pixelReflection(canvas,cx-size*.36f,cy+size*.50f,size*.72f,size*.25f,tint)
  }
  private val menuPaint=Paint(Paint.ANTI_ALIAS_FLAG)
  init{setBackgroundColor(Color.BLACK)}
  fun reset(){levels=FloatArray(31);peaks=FloatArray(31);holds=IntArray(31);trails.forEach{it.fill(0f)};invalidate()}
  override fun onTouchEvent(event: MotionEvent): Boolean {
   if(event.action==MotionEvent.ACTION_UP) {
    if(menuBounds.contains(event.x,event.y)) { showMenu();return true }
    for(i in buttons.indices) if(buttons[i].contains(event.x,event.y)) {transport(i);return true}
   }
   return true
  }
  fun consume(s:FloatArray){latest=s;invalidate()}
  fun consumeFft(fft:ByteArray,rate:Int){val out=FloatArray(256);for(i in out.indices){val freq=30.0*(20000.0/30.0).pow((i+.5)/256.0);val k=(freq*fft.size/rate).roundToInt().coerceIn(1,fft.size/2-1);val re=fft[2*k].toInt();val im=fft[2*k+1].toInt();out[i]=(ln(1.0+hypot(re.toDouble(),im.toDouble()))/6).toFloat().coerceIn(0f,1f)};consume(out)}
  override fun onDraw(canvas:Canvas) {
   super.onDraw(canvas)
   val now=android.os.SystemClock.uptimeMillis()
   if(now-lastMetadataCheck>750) {updateTrack();lastMetadataCheck=now}
   val dt=((System.nanoTime()-last)/1e9f).coerceIn(.005f,.08f);last=System.nanoTime()
   val w=width.toFloat();val h=height.toFloat()
   val margin=w*.025f;val gap=w*.008f;val bw=(w-2*margin-(bands-1)*gap)/bands
   val segCount=24;val top=h*.14f;val base=h*.76f
   val pitch=(base-top)/segCount;val segGap=pitch*.16f;val sh=pitch-segGap
   for(i in 0 until bands) {
    val from=i*256/bands;val to=(i+1)*256/bands
    var raw=0f
    for(k in from until to)raw=max(raw,latest[k])
    val hz=30.0*(20000.0/30.0).pow((i+0.5)/bands.toDouble())
    val shelf=(1.0-(1.0-bassBalance.toDouble())/(1.0+(hz/220.0).pow(1.5))).toFloat()
    val weighted=(raw*shelf*sensitivity).coerceAtLeast(0f)
    val target=(weighted/(0.22f+weighted)).coerceIn(0f,1f)
    levels[i]=if(target>levels[i])levels[i]+(target-levels[i])*.70f
     else max(target,levels[i]-decay*dt*60)
    if(levels[i]>=peaks[i]) {peaks[i]=levels[i];holds[i]=25}
    else if(holds[i]>0)holds[i]-- else peaks[i]=max(levels[i],peaks[i]-.015f*dt*60)
    val active=(levels[i]*segCount).roundToInt()
    val x=margin+i*(bw+gap)
    for(j in 0 until segCount) {
     val y=base-(j+1)*pitch
     val lit=j<active
     val old=trails[i][j]
     val fade=when(glow){0->0f;1->.52f;2->.76f;else->.86f}
     val brightness=if(lit) 1f else old*fade.pow(dt*60f)
     trails[i][j]=brightness
     val rgb=colour(j.toFloat()/segCount, i)
     if(brightness>.025f && glow>0) {
      // Blur the optical halo, not the hard-edged luminous segment.
      glowPaint.maskFilter=BlurMaskFilter((if(glow==3) 19f else if(glow==2) 13f else 7f)*resources.displayMetrics.density,BlurMaskFilter.Blur.NORMAL)
      glowPaint.color=Color.argb((brightness*(if(glow==3) .82f else if(glow==2) .58f else .27f)*255).toInt().coerceIn(0,255),Color.red(rgb),Color.green(rgb),Color.blue(rgb))
      canvas.drawRect(x-3f,y-3f,x+bw+3f,y+sh+3f,glowPaint)
      glowPaint.maskFilter=null
     }
     // Every segment is a physical matrix of tiny separated square emitters.
     // Dark pixels remain visible through the smoke-glass background.
     val step=max(3.0f,min(7.0f,bw/9.0f))
     val sq=step*.79f
     paint.color=if(brightness>.025f)
      Color.argb((brightness*255).toInt().coerceIn(0,255),Color.red(rgb),Color.green(rgb),Color.blue(rgb))
      else Color.rgb(5,9,10)
     var py=y+step*.10f
     while(py+sq<=y+sh+step*.25f) {
      var px=x+step*.08f
      while(px+sq<=x+bw+step*.2f) {
       canvas.drawRect(px,py,px+sq,py+sq,paint)
       px+=step
      }
      py+=step
     }
    }
    if(peakHold) {
     val p=(peaks[i]*segCount).toInt().coerceIn(0,segCount-1)
     paint.color=colour(p.toFloat()/segCount,i)
     val step=max(3f,min(7f,bw/9f));val yPeak=base-(p+1)*pitch
     var px=x;while(px+step*.75f<x+bw){canvas.drawRect(px,yPeak,px+step*.75f,yPeak+sh,paint);px+=step}
    }
    // Smoked-glass diffuse reflection beneath each column.
    val reflected=levels[i].coerceIn(0f,1f)
    if(glow>0 && reflected>.05f) {
     val reflection=Paint(Paint.ANTI_ALIAS_FLAG)
     val tint=colour(.16f,i)
     val bright=Color.argb((reflected*125).toInt().coerceIn(0,255), Color.red(tint),Color.green(tint),Color.blue(tint))
     val dim=Color.argb(18,Color.red(tint),Color.green(tint),Color.blue(tint))
     reflection.shader=LinearGradient(0f,base+4f,0f,base+h*.075f,
      intArrayOf(bright,dim,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
     canvas.drawRoundRect(x,base+5f,x+bw,base+h*.075f,5f,5f,reflection)
    }
   }
   // Live now-playing title: deliberately THIN, uppercase, dotted sans serif.
   // Enter briskly then hold, continuously scroll, remain static or hide.
   if(marqueeMode!=3 && trackText.isNotBlank()) {
    val pitch=(h*.0048f).coerceIn(2.5f,5.2f)
    val textMask=matrix(trackText,h*.068f,pitch,bold=false,italic=false)
    val textW=textMask.width
    val elapsed=(now-trackChangedAt).coerceAtLeast(0L)/1000f
    val speed=w*.85f
    val holdX=(w-textW)/2f
    val x=when(marqueeMode) {
     0 -> max(holdX,w-elapsed*speed) // enter, then remain in place
     1 -> w-(elapsed*speed)%(w+textW) // continuous scroll
     else -> holdX // static
    }
    canvas.save();canvas.clipRect(0f,0f,w,h*.132f)
    ledMatrix(canvas,textMask,x,h*.012f,accent(),.48f,.65f,rainbow=true)
    canvas.restore()
   }
   // The LIVE media-session state determines whether the centre control is
   // circled PLAY (stopped/paused) or PAUSE (currently playing).
   val controlY=h*.894f
   val iconSize=h*.145f
   val centers=floatArrayOf(w*.375f,w*.500f,w*.625f)
   for(i in 0..2) {
    val cx=centers[i]
    buttons[i].set(cx-iconSize*.67f,controlY-iconSize*.67f,
                   cx+iconSize*.67f,controlY+iconSize*.67f)
    ledTransport(canvas,i,cx,controlY,iconSize,isMediaPlaying,accent(i,3))
   }
   // Bottom-right: distinct sporty, HEAVY italic ST branding, red at all times.
   // Bold italic ANALYSER matches the active colour theme. Both are sampled
   // into thousands of square LED emitters, never flat fonts or a static image.
   val logoPitch=(h*.0049f).coerceIn(2.6f,5.3f)
   val stMask=matrix("ST",h*.100f,logoPitch,bold=true,italic=true)
   val nameMask=matrix("-ANALYSER",h*.082f,logoPitch,bold=true,italic=true)
   val join=logoPitch*.2f
   val right=w-w*.019f
   val x=right-stMask.width-nameMask.width-join
   val bottom=h*.926f
   val yST=bottom-stMask.height
   val yName=bottom-nameMask.height
   val stRed=Color.rgb(255,35,52)
   ledMatrix(canvas,stMask,x,yST,stRed,.94f,1.25f)
   ledMatrix(canvas,nameMask,x+stMask.width+join,yName,accent(),.90f,1.14f,rainbow=true)
   pixelReflection(canvas,x,bottom,stMask.width,h*.055f,stRed)
   pixelReflection(canvas,x+stMask.width+join,bottom,nameMask.width,h*.055f,accent())
   menuBounds.set(x-logoPitch*3,h*.795f,w,h*.99f)
   postInvalidateDelayed(33)
  }
  private fun colour(r:Float, band:Int=0):Int {
   val v=r.coerceIn(0f,1f)
   return when(theme) {
    1 -> if(v<.60f) Color.rgb(0,206,214) else Color.rgb(117,229,255)
    2 -> if(v<.55f) Color.rgb(255,145,0) else Color.rgb(255,218,65)
    3 -> Color.HSVToColor(floatArrayOf((240f-v*300f+band*5f+360f)%360f,.99f,1f))
    4 -> if(v<.27f) Color.rgb(255,192,36) else if(v<.56f) Color.rgb(255,112,16) else Color.rgb(255,35,25)
    5 -> Color.HSVToColor(floatArrayOf(280f-100f*v, .9f,1f))
    6 -> Color.HSVToColor(floatArrayOf(145f+85f*v, .95f,1f))
    // Short green foundation. Yellow/orange/red appear considerably earlier.
    else -> if(v<.24f) Color.rgb(25,225,75)
      else if(v<.43f) Color.rgb(255,218,30)
      else if(v<.59f) Color.rgb(255,131,19)
      else Color.rgb(255,42,32)
   }
  }
 }
}
