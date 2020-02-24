precision mediump float;

varying vec2 v_textCoord;

uniform sampler2D u_texture;

uniform float u_width;
uniform float u_height;


void main() {
    vec4 textureColor = texture2D(u_texture, v_textCoord);
    gl_FragColor = textureColor;
}