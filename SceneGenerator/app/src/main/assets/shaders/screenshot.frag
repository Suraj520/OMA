precision mediump float;

varying vec2 v_textCoord;

uniform sampler2D u_texture;

void main() {
    //Non devo manipolare le coordinate Y: il formato bitmap ha un origine bottom-left
    vec2 uv = vec2(v_textCoord.x, v_textCoord.y);
    vec4 textureColor = texture2D(u_texture, uv);
    gl_FragColor = textureColor;
}