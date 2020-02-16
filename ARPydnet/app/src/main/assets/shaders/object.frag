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

uniform vec4 u_LightingParameters;
uniform vec4 u_MaterialParameters;
uniform vec4 u_ColorCorrectionParameters;

varying vec3 v_ViewPosition;
varying vec3 v_ViewNormal;

varying vec2 v_TexCoord;

uniform vec4 u_ObjColor;

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


        vec4 textureVector = texture2D(u_maskTexture, texcoord);
        float pydnetDistance = textureVector.r * u_scaleFactor * u_quantizerFactor;

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
    vec3 viewLightDirection = u_LightingParameters.xyz;
    vec3 colorShift = u_ColorCorrectionParameters.rgb;
    float averagePixelIntensity = u_ColorCorrectionParameters.a;

    float materialAmbient = u_MaterialParameters.x;
    float materialDiffuse = u_MaterialParameters.y;
    float materialSpecular = u_MaterialParameters.z;
    float materialSpecularPower = u_MaterialParameters.w;

    // Normalize varying parameters, because they are linearly interpolated in the vertex shader.
    vec3 viewFragmentDirection = normalize(v_ViewPosition);
    vec3 viewNormal = normalize(v_ViewNormal);

    // Flip the y-texture coordinate to address the texture from top-left.
    vec4 objectColor = texture2D(u_Texture, vec2(v_TexCoord.x, 1.0 - v_TexCoord.y));

    // Apply color to grayscale image only if the alpha of u_ObjColor is
    // greater and equal to 255.0.
    if (u_ObjColor.a >= 255.0) {
      float intensity = objectColor.r;
      objectColor.rgb = u_ObjColor.rgb * intensity / 255.0;
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

        gl_FragColor.rgb = color.r * depthColorTexture.rgb;
    }else{
        gl_FragColor.rgb = color;
    }

    gl_FragColor.a = objectColor.a;
}
