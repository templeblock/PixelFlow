/**
 * 
 * PixelFlow | Copyright (C) 2016 Thomas Diewald - http://thomasdiewald.com
 * 
 * A Processing/Java library for high performance GPU-Computing (GLSL).
 * MIT License: https://opensource.org/licenses/MIT
 * 
 */




package OpticalFlow_MovieFluid;



import java.util.Locale;

import com.thomasdiewald.pixelflow.java.Fluid;
import com.thomasdiewald.pixelflow.java.OpticalFlow;
import com.thomasdiewald.pixelflow.java.PixelFlow;
import com.thomasdiewald.pixelflow.java.dwgl.DwGLSLProgram;
import com.thomasdiewald.pixelflow.java.filter.Filter;

import controlP5.Accordion;
import controlP5.CheckBox;
import controlP5.ControlP5;
import controlP5.Group;
import controlP5.Numberbox;
import controlP5.RadioButton;
import controlP5.Toggle;
import processing.core.*;
import processing.opengl.PGraphics2D;
import processing.video.Capture;
import processing.video.Movie;


public class OpticalFlow_MovieFluid extends PApplet {
 
  
 private class MyFluidData implements Fluid.FluidData{
    
    
    @Override
    // this is called during the fluid-simulation update step.
    public void update(Fluid fluid) {
    
      float px, py, vx, vy, radius, vscale, r, g, b, a;

      boolean mouse_input = !cp5.isMouseOver() && mousePressed;
      if(mouse_input ){
  
        vscale = 15;
        px     = mouseX;
        py     = height-mouseY;
        vx     = (mouseX - pmouseX) * +vscale;
        vy     = (mouseY - pmouseY) * -vscale;
        
        if(mouseButton == LEFT){
          radius = 20;
          fluid.addVelocity(px, py, radius, vx, vy);
        }
        if(mouseButton == CENTER){
          radius = 50;
          fluid.addDensity (px, py, radius, 1.0f, 0.0f, 0.40f, 1f, 1);
        }
        if(mouseButton == RIGHT){
          radius = 15;
          fluid.addTemperature(px, py, radius, 15f);
        }
        
      }
      
      addDensityTexture_cam(fluid, opticalflow);
      addVelocityTexture   (fluid, opticalflow);
      addTemperatureTexture(fluid, opticalflow);
    }
    

    public void addDensityTexture_cam(Fluid fluid, OpticalFlow opticalflow){
      int[] pg_tex_handle = new int[1];
      
      if( !pg_movie_a.getTexture().available() ) return;
      
      float mix = opticalflow.UPDATE_STEP > 1 ? 0.05f : 1.0f;
      
      context.begin();
      context.getGLTextureHandle(pg_movie_a, pg_tex_handle);
      context.beginDraw(fluid.tex_density.dst);
      DwGLSLProgram shader = context.createShader("examples/OpticalFlow_MovieFluid/data/addDensityCam.frag");
      shader.begin();
      shader.uniform2f     ("wh"        , fluid.fluid_w, fluid.fluid_h);                                                                   
      shader.uniform1i     ("blend_mode", 6);   
      shader.uniform1f     ("mix_value" , mix);     
      shader.uniform1f     ("multiplier", 1f);     
//      shader.uniformTexture("tex_ext"   , opticalflow.tex_frames.src);
      shader.uniformTexture("tex_ext"   , pg_tex_handle[0]);
      shader.uniformTexture("tex_src"   , fluid.tex_density.src);
      shader.drawFullScreenQuad();
      shader.end();
      context.endDraw();
      context.end("app.addDensityTexture");
      fluid.tex_density.swap();
    }
    
    
    
    // custom shader, to add temperature from a texture (PGraphics2D) to the fluid.
    public void addTemperatureTexture(Fluid fluid, OpticalFlow opticalflow){

      context.begin();
      context.beginDraw(fluid.tex_temperature.dst);
      DwGLSLProgram shader = context.createShader("examples/OpticalFlow_MovieFluid/data/addTemperature.frag");
      shader.begin();
      shader.uniform2f     ("wh"        , fluid.fluid_w, fluid.fluid_h);                                                                   
      shader.uniform1i     ("blend_mode", 1);   
      shader.uniform1f     ("mix_value" , 0.1f);     
      shader.uniform1f     ("multiplier", 0.01f);     
      shader.uniformTexture("tex_ext"   , opticalflow.frameCurr.velocity);
      shader.uniformTexture("tex_src"   , fluid.tex_temperature.src);
      shader.drawFullScreenQuad();
      shader.end();
      context.endDraw();
      context.end("app.addTemperatureTexture");
      fluid.tex_temperature.swap();
    }
    
    // custom shader, to add density from a texture (PGraphics2D) to the fluid.
    public void addVelocityTexture(Fluid fluid, OpticalFlow opticalflow){
      context.begin();
      context.beginDraw(fluid.tex_velocity.dst);
      DwGLSLProgram shader = context.createShader("data/addVelocity.frag");
      shader.begin();
      shader.uniform2f     ("wh"             , fluid.fluid_w, fluid.fluid_h);                                                                   
      shader.uniform1i     ("blend_mode"     , 2);    
      shader.uniform1f     ("multiplier"     , 0.5f);   
      shader.uniform1f     ("mix_value"      , 0.1f);
      shader.uniformTexture("tex_opticalflow", opticalflow.frameCurr.velocity);
      shader.uniformTexture("tex_velocity_old", fluid.tex_velocity.src);
      shader.drawFullScreenQuad();
      shader.end();
      context.endDraw();
      context.end("app.addDensityTexture");
      fluid.tex_velocity.swap();
    }
 
  }
  
 
 
 
 

 
  int view_w = 1280;
  int view_h = 720;
  int view_x = 237;
  int view_y = 0;
  
  
  int gui_w = 200;

  int pg_movie_w = view_w - gui_w;
  int pg_movie_h = view_h;
  

  
  //main library context
  PixelFlow context;
  
  // optical flow
  OpticalFlow opticalflow;
  
  // buffer for the movie-image
  PGraphics2D pg_movie_a, pg_movie_b; 

  // offscreen render-target
  PGraphics2D pg_oflow;
  
  // Movie
  Movie movie;
  TimeLine timeline;
  
  PFont font;
  
  
  int fluidgrid_scale = 1;
  Fluid fluid;
  MyFluidData cb_fluid_data;

  


  
  // some state variables for the GUI/display
  public int     BACKGROUND_COLOR  = 0;
  public boolean DISPLAY_MOVIE   = true;
  public boolean APPLY_GRAYSCALE = false;
  public boolean APPLY_BILATERAL = true;
  public int     VELOCITY_LINES  = 6;
  

  
  
  public void settings() {
    size(view_w, view_h, P2D);
    smooth(4);
  }

  public void setup() {
    
    surface.setLocation(view_x, view_y);
    
    // main library context
    context = new PixelFlow(this);
    context.print();
    context.printGL();
      
    // OF
    opticalflow = new OpticalFlow(context, pg_movie_w, pg_movie_h);
    opticalflow.param.display_mode = 3;
    
    // fluid solver
    fluid = new Fluid(context, pg_movie_w, pg_movie_h, fluidgrid_scale);
    // some fluid parameters
    fluid.param.dissipation_density     = 0.95f;
    fluid.param.dissipation_velocity    = 0.90f;
    fluid.param.dissipation_temperature = 0.70f;
    fluid.param.vorticity               = 0.30f;

    // calback for adding fluid data
    cb_fluid_data = new MyFluidData();
    fluid.addCallback_FluiData(cb_fluid_data);
   
    pg_movie_a = (PGraphics2D) createGraphics(pg_movie_w, pg_movie_h, P2D);
    pg_movie_a.noSmooth();
    pg_movie_a.beginDraw();
    pg_movie_a.background(0);
    pg_movie_a.endDraw();
    
    pg_movie_b = (PGraphics2D) createGraphics(pg_movie_w, pg_movie_h, P2D);
    pg_movie_b.noSmooth();
    
    pg_oflow = (PGraphics2D) createGraphics(pg_movie_w, pg_movie_h, P2D);
    pg_oflow.smooth(4);
    
    
//    movie = new Movie(this, "examples/data/GoPro_ Owl Dance-Off.mp4");
    movie = new Movie(this, "examples/data/Pulp_Fiction_Dance_Scene.mp4");
//    movie = new Movie(this, "examples/data/Michael Jordan Iconic Free Throw Line Dunk.mp4");
    movie.loop();
    
    timeline = new TimeLine(movie);
    timeline.setPosition(0, height-20, pg_movie_w, 20);
    
    // processing font
    font = createFont("SourceCodePro-Regular.ttf", 12);
        
    createGUI();
    
    background(0);
    frameRate(60);
  }
  

  

  

  public void draw() {
    
    if( movie.available() ){
      movie.read();
      
      int movie_w = movie.width;
      int movie_h = movie.height;
      
      float mov_w_fit = pg_movie_w;
      float mov_h_fit = (pg_movie_w/(float)movie_w) * movie_h;
      
      if(mov_h_fit > pg_movie_h){
        mov_h_fit = pg_movie_h;
        mov_w_fit = (pg_movie_h/(float)movie_h) * movie_w;
      }
      
      
      
      // render to offscreenbuffer
      pg_movie_a.beginDraw();
      pg_movie_a.background(0);
      pg_movie_a.imageMode(CENTER);
      pg_movie_a.pushMatrix();
      pg_movie_a.translate(pg_movie_w/2f, pg_movie_h/2f);
      pg_movie_a.scale(0.95f);
      pg_movie_a.image(movie, 0, 0, mov_w_fit, mov_h_fit);
      pg_movie_a.popMatrix();
      pg_movie_a.endDraw();
      
      // apply filters (not necessary)
      if(APPLY_GRAYSCALE){
        Filter.get(context).luminance.apply(pg_movie_a, pg_movie_a);
      }
      if(APPLY_BILATERAL){
        Filter.get(context).bilateral.apply(pg_movie_a, pg_movie_b, 5, 0.10f, 4);
        swapCamBuffer();
      }
      

      // update Optical Flow
      opticalflow.update(pg_movie_a);
    }
    

    if(UPDATE_FLUID){
      fluid.update();
    }
  
    

    // render Optical Flow
    pg_oflow.beginDraw();
    pg_oflow.background(BACKGROUND_COLOR);
    if(DISPLAY_MOVIE){// && ADD_DENSITY_MODE == 0){
      pg_oflow.image(pg_movie_a, 0, 0);
    }
    pg_oflow.endDraw();
    
    
    // add fluid stuff to rendering
    if(DISPLAY_FLUID_TEXTURES){
      fluid.renderFluidTextures(pg_oflow, DISPLAY_fluid_texture_mode);
    }
    
    if(DISPLAY_FLUID_VECTORS){
      fluid.renderFluidVectors(pg_oflow, 10);
    }
    
    // add flow-vectors to the image
    if(opticalflow.param.display_mode == 2){
      opticalflow.renderVelocityShading(pg_oflow);
    }
    opticalflow.renderVelocityStreams(pg_oflow, VELOCITY_LINES);
    
    // display result
    background(0);
    image(pg_oflow, 0, 0);
    
    timeline.draw(mouseX, mouseY);
    
    // info
    String txt_fps = String.format(getClass().getName()+ "   [size %d/%d]   [frame %d]   [fps %6.2f]", view_w, view_h, opticalflow.UPDATE_STEP, frameRate);
    surface.setTitle(txt_fps);
   
  }
  
  

  
  public void mouseReleased(){
    if(timeline.inside(mouseX, mouseY)){
      float movie_pos = map(mouseX, 0, pg_movie_a.width, 0, movie.duration());
      movie.jump(movie_pos);
      System.out.println(movie_pos);
    }
  }

  
  class TimeLine{
    float x, y, w, h;
    Movie movie;
    public TimeLine(Movie movie){
      this.movie = movie;

    }
    
    public void setPosition(float x, float y, float w, float h){
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
    }
    
    public boolean inside(float mx, float my){
      return mx >= x && mx <= (x+w) && my >= y && my <= (y+h);
    }
    
    
    
    public void draw(float mx, float my){
      float time      = movie.time();
      float duration  = movie.duration();
      float movie_pos = w * time / duration;
      String time_str = String.format(Locale.ENGLISH, "%1.2f", time);
      
      // timeline
      fill(64, 200);
      noStroke();
      rect(x, y, w, h);
      
      // time handle
      fill(200, 200);
      rect(x+movie_pos-25, y, 50, 20, 8);
      
      // time, as text in seconds
      fill(0);
      textFont(font);
      textAlign(CENTER, CENTER);
      text(time_str, x + movie_pos, y + h/2 - 2);
      
      if(inside(mx, my)){
        float hoover_pos = duration * (mx - x) / w;
        String hoover_str = String.format(Locale.ENGLISH, "%1.2f", hoover_pos);
        
        // time handle
        fill(200, 50);
        rect(mx-25, y, 50, 20, 8);
        
        // time, as text in seconds
        fill(200, 100);
        textFont(font);
        textAlign(CENTER, CENTER);
        text(hoover_str, mx, y + h/2 - 2);
      }
      
      
      
      
    }
  }
  
  
  void swapCamBuffer(){
    PGraphics2D tmp = pg_movie_a;
    pg_movie_a = pg_movie_b;
    pg_movie_b = tmp;
  }
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  

  
  boolean UPDATE_FLUID = true;
  
  boolean DISPLAY_FLUID_TEXTURES  = true;
  boolean DISPLAY_FLUID_VECTORS   = !true;
  boolean DISPLAY_PARTICLES       = !true;
  
  int     DISPLAY_fluid_texture_mode = 0;
  
  public void keyReleased(){
    if(key == 'p') fluid_togglePause(); // pause / unpause simulation
    if(key == '+') fluid_resizeUp();    // increase fluid-grid resolution
    if(key == '-') fluid_resizeDown();  // decrease fluid-grid resolution
    if(key == 'r') fluid_reset();       // restart simulation
    
    if(key == '1') DISPLAY_fluid_texture_mode = 0; // density
    if(key == '2') DISPLAY_fluid_texture_mode = 1; // temperature
    if(key == '3') DISPLAY_fluid_texture_mode = 2; // pressure
    if(key == '4') DISPLAY_fluid_texture_mode = 3; // velocity
    
    if(key == 'q') DISPLAY_FLUID_TEXTURES = !DISPLAY_FLUID_TEXTURES;
    if(key == 'w') DISPLAY_FLUID_VECTORS  = !DISPLAY_FLUID_VECTORS;
    if(key == 'e') DISPLAY_PARTICLES      = !DISPLAY_PARTICLES;
  }
  

  public void fluid_resizeUp(){
    fluid.resize(width, height, fluidgrid_scale = max(1, --fluidgrid_scale));
  }
  public void fluid_resizeDown(){
    fluid.resize(width, height, ++fluidgrid_scale);
  }
  public void fluid_reset(){
    fluid.reset();
  }
  public void fluid_togglePause(){
    UPDATE_FLUID = !UPDATE_FLUID;
  }
  public void setDisplayMode(int val){
    DISPLAY_fluid_texture_mode = val;
    DISPLAY_FLUID_TEXTURES = DISPLAY_fluid_texture_mode != -1;
  }
  public void setDisplayVelocityVectors(int val){
    DISPLAY_FLUID_VECTORS = val != -1;
  }
  public void setDisplayParticles(int val){
    DISPLAY_PARTICLES = val != -1;
  }

  
  
  
  
  
  
  
  
  
  
  
  
  ControlP5 cp5;
  
  public void createGUI(){
    cp5 = new ControlP5(this);
    
    int sx = 100, sy = 14;
    int px = 10, py = 20, oy = (int)(sy*1.5f);
    
    
    
    
    
    

    
    Group group_fluid = cp5.addGroup("fluid controls")
//      .setPosition(20, 40)
      .setHeight(20).setWidth(180)
      .setBackgroundHeight(370)
      .setBackgroundColor(color(16, 180)).setColorBackground(color(16, 180));
      group_fluid.getCaptionLabel().align(LEFT, CENTER);
    
      cp5.addButton("reset").setGroup(group_fluid).plugTo(this, "fluid_reset").setWidth(75).setPosition(px, py);
      cp5.addButton("+"    ).setGroup(group_fluid).plugTo(this, "fluid_resizeUp").setWidth(25).setPosition(px+=85, py);
      cp5.addButton("-"    ).setGroup(group_fluid).plugTo(this, "fluid_resizeDown").setWidth(25).setPosition(px+=30, py);
      
      px = 10;
      
      cp5.addSlider("velocity").setGroup(group_fluid).setSize(sx, sy).setPosition(px, py+=oy *2)
      .setRange(0, 1).setValue(fluid.param.dissipation_velocity)
      .plugTo(fluid.param, "dissipation_velocity").linebreak();
      
      cp5.addSlider("density").setGroup(group_fluid).setSize(sx, sy).setPosition(px, py+=oy)
      .setRange(0, 1).setValue(fluid.param.dissipation_density)
      .plugTo(fluid.param, "dissipation_density").linebreak();
      
      cp5 .addSlider("temperature").setGroup(group_fluid).setSize(sx, sy).setPosition(px, py+=oy)
      .setRange(0, 1).setValue(fluid.param.dissipation_temperature)
      .plugTo(fluid.param, "dissipation_temperature").linebreak();
    
      cp5 .addSlider("vorticity").setGroup(group_fluid).setSize(sx, sy).setPosition(px, py+=oy)
      .setRange(0, 1).setValue(fluid.param.vorticity)
      .plugTo(fluid.param, "vorticity").linebreak();
          
      cp5.addSlider("iterations").setGroup(group_fluid).setSize(sx, sy).setPosition(px, py+=oy)
      .setRange(0, 80).setValue(fluid.param.num_jacobi_projection)
      .plugTo(fluid.param, "num_jacobi_projection").linebreak();
            
      cp5.addSlider("timestep").setGroup(group_fluid).setSize(sx, sy).setPosition(px, py+=oy)
      .setRange(0, 1).setValue(fluid.param.timestep)
      .plugTo(fluid.param, "timestep").linebreak();
          
      cp5.addSlider("gridscale").setGroup(group_fluid).setSize(sx, sy).setPosition(px, py+=oy)
      .setRange(0, 50).setValue(fluid.param.gridscale)
      .plugTo(fluid.param, "gridscale").linebreak();
      
      RadioButton rb_setDisplayMode = cp5.addRadio("setDisplayMode").setGroup(group_fluid).setSize(80,18).setPosition(px, py+=oy)
          .setSpacingColumn(2).setSpacingRow(2).setItemsPerRow(2)
          .addItem("Density"    ,0)
          .addItem("Temperature",1)
          .addItem("Pressure"   ,2)
          .addItem("Velocity"   ,3)
          .activate(0);
      for(Toggle toggle : rb_setDisplayMode.getItems()) toggle.getCaptionLabel().alignX(CENTER);
      
      cp5.addRadio("setDisplayVelocityVectors").setGroup(group_fluid).setPosition(px, py+=oy)
          .setPosition(10, 255).setSize(18,18)
          .setSpacingColumn(2).setSpacingRow(2).setItemsPerRow(1)
          .addItem("Velocity Vectors",0)
          ;
      
      Numberbox bg = cp5.addNumberbox("BACKGROUND_COLOR").setGroup(group_fluid).setSize(80,sy).setPosition(px, py+=(int)(oy*3.5f))
      .setMin(0).setMax(255).setScrollSensitivity(1) .setValue(BACKGROUND_COLOR);
      bg.getCaptionLabel().align(LEFT, CENTER).getStyle().setMarginLeft(85);
      
      
      Toggle cam = cp5.addToggle("display movie").setGroup(group_fluid).setSize(80, sy).setPosition(px, py+=oy)
      .plugTo(this, "DISPLAY_MOVIE").setValue(DISPLAY_MOVIE).linebreak();
      cam.getCaptionLabel().align(CENTER, CENTER);
      
      group_fluid.close();
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    py = 10;
    
    
    
    
    Group group_oflow = cp5.addGroup("OpticalFlow")
//    .setPosition(width-gui_w, 20)
    .setHeight(20).setWidth(gui_w)
    .setBackgroundHeight(view_h).setBackgroundColor(color(16, 180)).setColorBackground(color(16, 180));
    group_oflow.getCaptionLabel().align(LEFT, CENTER);
    
    cp5.addSlider("blur input").setGroup(group_oflow).setSize(sx, sy).setPosition(px, py+=oy)
    .setRange(0, 30).setValue(opticalflow.param.blur_input)
    .plugTo(opticalflow.param, "blur_input").linebreak();
    
    cp5.addSlider("blur flow").setGroup(group_oflow).setSize(sx, sy).setPosition(px, py+=oy)
    .setRange(0, 10).setValue(opticalflow.param.blur_flow)
    .plugTo(opticalflow.param, "blur_flow").linebreak();
    
    cp5.addSlider("temporal smooth").setGroup(group_oflow).setSize(sx, sy).setPosition(px, py+=oy)
    .setRange(0, 1).setValue(opticalflow.param.temporal_smoothing)
    .plugTo(opticalflow.param, "temporal_smoothing").linebreak();
    
    cp5.addSlider("flow scale").setGroup(group_oflow).setSize(sx, sy).setPosition(px, py+=oy)
    .setRange(0, 200f).setValue(opticalflow.param.flow_scale)
    .plugTo(opticalflow.param, "flow_scale").linebreak();

    cp5.addSlider("threshold").setGroup(group_oflow).setSize(sx, sy).setPosition(px, py+=oy)
    .setRange(0, 2.0f).setValue(opticalflow.param.threshold)
    .plugTo(opticalflow.param, "threshold").linebreak();
    
    cp5.addSpacer("display").setGroup(group_oflow).setPosition(px, py+=oy);

    CheckBox cb = cp5.addCheckBox("activeFilters").setGroup(group_oflow).setSize(18, 18).setPosition(px, py+=oy)
    .setItemsPerRow(1).setSpacingColumn(3).setSpacingRow(3)
    .addItem("grayscale"       , 0)
    .addItem("bilateral filter", 0)
    ;
    
    if(APPLY_GRAYSCALE) cb.activate(0);
    if(APPLY_BILATERAL) cb.activate(1);
    
    cp5.addSlider("line density").setGroup(group_oflow).setSize(sx, sy).setPosition(px, py+=(int)(oy*2.5))
    .setRange(1, 10).setValue(VELOCITY_LINES)
    .plugTo(this, "VELOCITY_LINES").linebreak();

    cp5.addRadio("setDisplayModeOpticalFlow").setGroup(group_oflow).setSize(18, 18).setPosition(px, py+=oy)
        .setSpacingColumn(40).setSpacingRow(2).setItemsPerRow(3)
        .addItem("dir", 0)
        .addItem("normal", 1)
        .addItem("Shading", 2)
        .activate(opticalflow.param.display_mode);

    group_oflow.open();
    
    
    
    
    Accordion accordion = cp5.addAccordion("acc")
        .setPosition(view_w-gui_w, 0)
        .setWidth(gui_w)
        .addItem(group_fluid)
        .addItem(group_oflow)
        ;

    accordion.setCollapseMode(Accordion.MULTI);
    accordion.open(0);
    accordion.open(1);
  }
  
  
  public void setDisplayModeOpticalFlow(int val){
    opticalflow.param.display_mode = val;
  }

  public void activeFilters(float[] val){
    APPLY_GRAYSCALE = (val[0] > 0);
    APPLY_BILATERAL = (val[1] > 0);
  }
  
  

  public static void main(String args[]) {
    PApplet.main(new String[] { OpticalFlow_MovieFluid.class.getName() });
  }
}