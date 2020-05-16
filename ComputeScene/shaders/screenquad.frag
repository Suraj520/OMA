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

varying vec2 v_backgroundTextCoord;

uniform sampler2D u_backgroundTexture;
uniform sampler2D u_inferenceTexture;
uniform sampler2D u_plasmaTexture;
uniform float u_plasmaEnabled;
uniform float u_plasmaFactor;
uniform float u_scaleFactor;
uniform float u_shiftFactor;
uniform float u_maxDepth;

void main() {
    vec2 textureCoord = vec2(v_backgroundTextCoord.x, 1.0 - v_backgroundTextCoord.y);
    vec4 tmp = texture2D(u_backgroundTexture, textureCoord);
    vec4 backgroundColor = vec4(tmp.b, tmp.g, tmp.r, 1.0);

    //Modalità visione depth: plasma color.
    if(u_plasmaEnabled > 0.5){
        vec4 inferenceVector = texture2D(u_inferenceTexture, textureCoord);

        //Ricavo il valire di distanza e lo moltiplico per il fattore colore.
        float predictedDistance = inferenceVector.r * u_scaleFactor + u_shiftFactor;

        if(predictedDistance < 0.0) predictedDistance = 0.0;
        if(predictedDistance > u_maxDepth) predictedDistance = u_maxDepth;
        predictedDistance = predictedDistance / u_maxDepth;

        //U: 0.0, 1.0, V: 0.0
        //Si tratta di una texture monodimensionale.
        vec4 plasmaVector = texture2D(u_plasmaTexture, vec2(predictedDistance * 5.0, 0.0));

        //backgroundTextureColor.rgb = backgroundTextureColor.rgb * plasmaVector.rgb;
        backgroundColor.rgb = backgroundColor.rgb * 0.3 + plasmaVector.rgb * 0.7;
    }


    gl_FragColor = backgroundColor;
}
