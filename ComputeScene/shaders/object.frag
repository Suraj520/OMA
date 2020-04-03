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

uniform float u_maskEnabled;

uniform float u_lowerDelta;

uniform vec2 u_windowSize;

uniform vec4 u_materialParameters;

varying vec2 v_texCoord;

uniform vec4 u_objColor;

varying vec3 v_worldPos;
uniform vec3 u_cameraPose;
uniform float u_scaleFactor;

void main() {

    if(u_maskEnabled > 0.5){
        //https://community.khronos.org/t/confused-about-gl-fragcoord-use-with-textures/67832/3
        //https://community.khronos.org/t/gl-fragcoord-z-gl-fragcoord-w-for-quick-depth-calculation-camera-to-fragment/68919/2
        vec2 texcoord = (gl_FragCoord.xy - vec2(0.5,0.5)) / u_windowSize;

        //Sistema opengl: origine in bottom left.
        //Sistema coordinate pydnet: origine in top-left

        //Inverto solo y
        texcoord = vec2(texcoord.x, 1.0-texcoord.y);

        vec4 inferenceVector = texture2D(u_inferenceTexture, texcoord);
        float pydnetDistance = inferenceVector.r * u_scaleFactor;

        //Fai un pò di ricerca su dove cade il punto...

        float dx = u_cameraPose.x-v_worldPos.x;
        float dy = u_cameraPose.y-v_worldPos.y;
        float dz = u_cameraPose.z-v_worldPos.z;

        float distance = sqrt(dx*dx+dy*dy+dz*dz);

        if(distance > pydnetDistance + u_lowerDelta){
            discard;
        }
    }

    // We support approximate sRGB gamma.
    const float kGamma = 0.4545454;
    const float kInverseGamma = 2.2;

    float materialAmbient = u_materialParameters.x;
    float materialSpecular = u_materialParameters.z;

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

    vec3 color = objectColor.rgb * materialAmbient + materialSpecular;
    // Apply SRGB gamma before writing the fragment color.
    color.rgb = pow(color, vec3(kGamma));

    gl_FragColor.a = objectColor.a;
}