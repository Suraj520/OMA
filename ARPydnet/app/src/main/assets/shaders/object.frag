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

uniform sampler2D u_texture;
uniform sampler2D u_inferenceTexture;
uniform sampler2D u_plasmaTexture;

uniform float u_maskEnabled;
uniform float u_plasmaEnabled;
uniform float u_plasmaFactor;

uniform vec2 u_windowSize;

uniform vec4 u_lightingParameters;
uniform vec4 u_materialParameters;
uniform vec4 u_colorCorrectionParameters;

varying vec3 v_viewPosition;
varying vec3 v_viewNormal;

varying vec2 v_texCoord;

uniform vec4 u_objColor;

varying vec3 v_worldPos;
uniform vec3 u_cameraPose;
uniform float u_scaleFactor;
uniform float u_screenOrientation;

void main() {

    if(u_maskEnabled > 0.5){
        //https://community.khronos.org/t/confused-about-gl-fragcoord-use-with-textures/67832/3
        //https://community.khronos.org/t/gl-fragcoord-z-gl-fragcoord-w-for-quick-depth-calculation-camera-to-fragment/68919/2
        vec2 texcoord = (gl_FragCoord.xy - vec2(0.5,0.5)) / u_windowSize;

        //Sistema opengl: origine in bottom left.
        //Sistema coordinate pydnet: origine in top-left

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

        //Fai un pò di ricerca su dove cade il punto...

        float dx = u_cameraPose.x-v_worldPos.x;
        float dy = u_cameraPose.y-v_worldPos.y;
        float dz = u_cameraPose.z-v_worldPos.z;

        float distance = sqrt(dx*dx+dy*dy+dz*dz);

        if(distance > pydnetDistance){
            discard;
        }
    }

    // We support approximate sRGB gamma.
    const float kGamma = 0.4545454;
    const float kInverseGamma = 2.2;
    const float kMiddleGrayGamma = 0.466;

    // Unpack lighting and material parameters for better naming.
    vec3 viewLightDirection = u_lightingParameters.xyz;
    vec3 colorShift = u_colorCorrectionParameters.rgb;
    float averagePixelIntensity = u_colorCorrectionParameters.a;

    float materialAmbient = u_materialParameters.x;
    float materialDiffuse = u_materialParameters.y;
    float materialSpecular = u_materialParameters.z;
    float materialSpecularPower = u_materialParameters.w;

    // Normalize varying parameters, because they are linearly interpolated in the vertex shader.
    vec3 viewFragmentDirection = normalize(v_viewPosition);
    vec3 viewNormal = normalize(v_viewNormal);

    // Flip the y-texture coordinate to address the texture from top-left.
    vec4 objectColor = texture2D(u_texture, vec2(v_texCoord.x, 1.0 - v_texCoord.y));

    // Apply color to grayscale image only if the alpha of u_ObjColor is
    // greater and equal to 255.0.
    if (u_objColor.a >= 255.0) {
      float intensity = objectColor.r;
      objectColor.rgb = u_objColor.rgb * intensity / 255.0;
    }

    // Apply inverse SRGB gamma to the texture before making lighting calculations.
    objectColor.rgb = pow(objectColor.rgb, vec3(kInverseGamma));

    // Ambient light is unaffected by the light intensity.
    float ambient = materialAmbient;

    // Approximate a hemisphere light (not a harsh directional light).
    float diffuse = materialDiffuse *
            0.5 * (dot(viewNormal, viewLightDirection) + 1.0);

    // Compute specular light.
    vec3 reflectedLightDirection = reflect(viewLightDirection, viewNormal);
    float specularStrength = max(0.0, dot(viewFragmentDirection, reflectedLightDirection));
    float specular = materialSpecular *
            pow(specularStrength, materialSpecularPower);

    vec3 color = objectColor.rgb * (ambient + diffuse) + specular;
    // Apply SRGB gamma before writing the fragment color.
    color.rgb = pow(color, vec3(kGamma));
    // Apply average pixel intensity and color shift
    color *= colorShift * (averagePixelIntensity / kMiddleGrayGamma);

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
        inferenceValue = inferenceValue / 255.0f;

        //U: 0.0, 1.0, V: 0.0
        //Si tratta di una texture monodimensionale.
        vec4 plasmaVector = texture2D(u_plasmaTexture, vec2(inferenceValue, 0.0));

        gl_FragColor.rgb = color * plasmaVector.rgb;
    }else{
        gl_FragColor.rgb = color;
    }

    gl_FragColor.a = objectColor.a;
}
