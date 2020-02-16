/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

precision highp float;

uniform sampler2D u_Texture;
uniform sampler2D u_maskTexture;
uniform sampler2D u_depthColorTexture;

uniform float u_maskEnabled;
uniform float u_depthColorEnabled;

uniform vec2 u_windowSize;

uniform vec4 u_dotColor;
uniform vec4 u_lineColor;
uniform vec4 u_gridControl;  // dotThreshold, lineThreshold, lineFadeShrink, occlusionShrink

varying vec3 v_TexCoordAlpha;

varying vec3 v_worldPos;
uniform vec3 u_cameraPose;
uniform float u_scaleFactor;
uniform float u_quantizerFactor;
uniform float u_screenOrientation;

void main() {

    if(u_maskEnabled > 0.5){
        //https://community.khronos.org/t/confused-about-gl-fragcoord-use-with-textures/67832/3
        //https://community.khronos.org/t/gl-fragcoord-z-gl-fragcoord-w-for-quick-depth-calculation-camera-to-fragment/68919/2
        vec2 texcoord = (gl_FragCoord.xy - vec2(0.5,0.5)) / u_windowSize;


        //Devo trasformare le coordinate della texture a seconda dell'orientaento dello schermo.
        //Dritto: 0
        //Landscape con il caricatore a destra: 90
        //Sottosopra: 180
        //Landscape con il caricatore a sinistra: 270


        if(-0.5 < u_screenOrientation && u_screenOrientation < 0.5){
            //Dritto devo fare delle trasformazioni
            texcoord = vec2(1.0-texcoord.y, 1.0-texcoord.x);
        }

        if(89.5 < u_screenOrientation && u_screenOrientation < 90.5){
            //Landscape con caricatore a destra: Inverto solo y
            texcoord = vec2(texcoord.x, 1.0-texcoord.y);
        }

        if(179.5 < u_screenOrientation && u_screenOrientation < 180.5){
            //Sottosopra.
            texcoord = vec2(texcoord.y, texcoord.x);
        }

        if(269.5 < u_screenOrientation && u_screenOrientation < 270.5){
            //Landscape con caricatore a sinistra: Inverto solo x
            texcoord = vec2(1.0-texcoord.x, texcoord.y);
        }

        vec4 textureVector = texture2D(u_maskTexture, texcoord);
        float pydnetDistance = textureVector.r * u_scaleFactor * u_quantizerFactor;

        float dx = u_cameraPose.x-v_worldPos.x;
        float dy = u_cameraPose.y-v_worldPos.y;
        float dz = u_cameraPose.z-v_worldPos.z;

        float distance = sqrt(dx*dx+dy*dy+dz*dz);

        if(distance > pydnetDistance){
            discard;
        }
    }

    vec4 control = texture2D(u_Texture, v_TexCoordAlpha.xy);
    float dotScale = v_TexCoordAlpha.z;
    float lineFade = max(0.0, u_gridControl.z * v_TexCoordAlpha.z - (u_gridControl.z - 1.0));
    vec3 color = (control.r * dotScale > u_gridControl.x) ? u_dotColor.rgb
               : (control.g > u_gridControl.y)            ? u_lineColor.rgb * lineFade
                                                          : (u_lineColor.rgb * 0.25 * lineFade) ;

    if(u_depthColorEnabled > 0.5){
        //https://community.khronos.org/t/confused-about-gl-fragcoord-use-with-textures/67832/3
        vec2 texcoord = (gl_FragCoord.xy - vec2(0.5,0.5)) / u_windowSize;

        //Devo trasformare le coordinate della texture a seconda dell'orientaento dello schermo.
        //Dritto: 0
        //Landscape con il caricatore a destra: 90
        //Sottosopra: 180
        //Landscape con il caricatore a sinistra: 270


        if(-0.5 < u_screenOrientation && u_screenOrientation < 0.5){
            //Dritto devo fare delle trasformazioni
            texcoord = vec2(1.0-texcoord.y, 1.0-texcoord.x);
        }

        if(89.5 < u_screenOrientation && u_screenOrientation < 90.5){
            //Landscape con caricatore a destra: Inverto solo y
            texcoord = vec2(texcoord.x, 1.0-texcoord.y);
        }

        if(179.5 < u_screenOrientation && u_screenOrientation < 180.5){
            //Sottosopra.
            texcoord = vec2(texcoord.y, texcoord.x);
        }

        if(269.5 < u_screenOrientation && u_screenOrientation < 270.5){
            //Landscape con caricatore a sinistra: Inverto solo x
            texcoord = vec2(1.0-texcoord.x, texcoord.y);
        }

        vec4 depthColorTexture = texture2D(u_depthColorTexture, texcoord);

        gl_FragColor = vec4(color.r * depthColorTexture.rgb, v_TexCoordAlpha.z * u_gridControl.w);
    }else{
        gl_FragColor = vec4(color, v_TexCoordAlpha.z * u_gridControl.w);
    }
}
