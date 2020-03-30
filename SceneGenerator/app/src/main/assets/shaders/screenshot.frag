precision mediump float;

varying vec2 v_textCoord;

uniform sampler2D u_texture;

void main() {
    //Devo manipolare le coordinate in modo da invertire la y.
    vec2 uv = vec2(v_textCoord.x, 1.0-v_textCoord.y);
    vec4 textureColor = texture2D(u_texture, uv);
    gl_FragColor = textureColor;
}