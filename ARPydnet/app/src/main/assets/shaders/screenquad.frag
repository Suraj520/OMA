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
#extension GL_OES_EGL_image_external : require

precision mediump float;

varying vec2 v_backgroundTextCoord;
varying vec2 v_plasmaTextCoord;

uniform samplerExternalOES u_backgroundTexture;

uniform sampler2D u_inferenceTexture;
uniform sampler2D u_plasmaTexture;

uniform float u_plasmaEnabled;
uniform float u_plasmaFactor;

void main() {
    vec4 backgroundTextureColor = texture2D(u_backgroundTexture, v_backgroundTextCoord);

    //Modalità visione depth: plasma color.
    if(u_plasmaEnabled > 0.5){
        vec4 inferenceVector = texture2D(u_inferenceTexture, v_plasmaTextCoord);

        //Ricavo il valire di distanza e lo moltiplico per il fattore colore.
        float inferenceValue = inferenceVector.r * u_plasmaFactor;

        //Normalizzazione.
        if(inferenceValue < 0.0) inferenceValue = 0.0;
        if(inferenceValue > 255.0) inferenceValue = 255.0;
        inferenceValue = inferenceValue / 255.0;

        //U: 0.0, 1.0, V: 0.0
        //Si tratta di una texture monodimensionale.
        vec4 plasmaVector = texture2D(u_plasmaTexture, vec2(inferenceValue, 0.0));

        //backgroundTextureColor.rgb = backgroundTextureColor.rgb * plasmaVector.rgb;
        backgroundTextureColor.rgb = plasmaVector.rgb;
    }

    gl_FragColor = backgroundTextureColor;
}
