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

precision mediump float;

uniform sampler2D u_texture;
uniform sampler2D u_inferenceTexture;
uniform sampler2D u_plasmaTexture;
uniform sampler2D u_rainTexture;

uniform float u_rainEnabled;
uniform vec2 u_rainResolution;
uniform float u_time;

uniform float u_maskEnabled;

uniform float u_plasmaEnabled;
uniform float u_plasmaFactor;

uniform vec2 u_windowSize;

uniform vec4 u_dotColor;
uniform vec4 u_lineColor;
uniform vec4 u_gridControl;  // dotThreshold, lineThreshold, lineFadeShrink, occlusionShrink

varying vec3 v_texCoordAlpha;

varying vec3 v_worldPos;
uniform vec3 u_cameraPose;
uniform float u_scaleFactor;
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

        vec4 inferenceVector = texture2D(u_inferenceTexture, texcoord);
        float pydnetDistance = inferenceVector.r * u_scaleFactor;

        float dx = u_cameraPose.x-v_worldPos.x;
        float dy = u_cameraPose.y-v_worldPos.y;
        float dz = u_cameraPose.z-v_worldPos.z;

        float distance = sqrt(dx*dx+dy*dy+dz*dz);

        if(distance > pydnetDistance + 0.05 || distance < pydnetDistance - 0.05){
            discard;
        }
    }

    vec4 control = texture2D(u_texture, v_texCoordAlpha.xy);
    float dotScale = v_texCoordAlpha.z;
    float lineFade = max(0.0, u_gridControl.z * v_texCoordAlpha.z - (u_gridControl.z - 1.0));
    vec3 color = (control.r * dotScale > u_gridControl.x) ? u_dotColor.rgb
               : (control.g > u_gridControl.y)            ? u_lineColor.rgb * lineFade
                                                          : (u_lineColor.rgb * 0.25 * lineFade) ;

    vec4 finalColor = vec4(color, v_texCoordAlpha.z * u_gridControl.w);

    //https://greentec.github.io/rain-drops-en/
    if(u_rainEnabled > 0.5){
        vec2 res = u_rainResolution.xy / u_windowSize.xy;
        vec2 uv = v_texCoordAlpha.xy;
        vec2 n = texture2D(u_rainTexture, uv * .1).rg;

        for (float r = 4.; r > 0.; r--) {
            vec2 x = res * r * .009;
            vec2 p = 6.28 * uv * x + (n - .5) * 2.5;
            vec2 s = sin(p);
            vec4 d = texture2D(u_rainTexture, floor(uv * x - 0.25 + 0.5) / x);
            float t = (s.x + s.y) * max(0.,1.-fract(u_time * (d.b+.1)+d.g) * 2.);

            if (d.r < (5.-r) * .12 && t > .5) {
                finalColor.rgb = finalColor.rgb*t;
            }
        }
    }

    if(u_plasmaEnabled > 0.5){
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

        vec4 inferenceVector = texture2D(u_inferenceTexture, texcoord);

        //Ricavo il valire di distanza e lo moltiplico per il fattore colore.
        float inferenceValue = inferenceVector.r * u_plasmaFactor;

        //Normalizzazione.
        if(inferenceValue < 0.0) inferenceValue = 0.0;
        if(inferenceValue > 255.0) inferenceValue = 255.0;
        inferenceValue = inferenceValue / 255.0;

        //U: 0.0, 1.0, V: 0.0
        //Si tratta di una texture monodimensionale.
        vec4 plasmaVector = texture2D(u_plasmaTexture, vec2(inferenceValue, 0.0));

        finalColor.rgb = finalColor.rgb * plasmaVector.rgb;
    }

    gl_FragColor = finalColor;
}
