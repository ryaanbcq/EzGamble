#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

// The lens parameters travel in the vertex attributes (no custom uniform needed):
// UV0 = this corner relative to the lens centre in framebuffer pixels (y down), UV1 = lens size in pixels,
// UV2 = (pixels per GUI pixel x 256, magnification x 1000), Color.r = pressed (0..1), Color.g = lean (-1..1 as 0..1).
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;

out vec2 local;
flat out vec2 lensSize;
flat out float k;
flat out float mag;
flat out float pressed;
flat out float lean;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    local = UV0;
    lensSize = vec2(UV1);
    k = float(UV2.x) / 256.0;
    mag = float(UV2.y) / 1000.0;
    pressed = Color.r;
    lean = (Color.g * 255.0 - 128.0) / 127.0;
}
