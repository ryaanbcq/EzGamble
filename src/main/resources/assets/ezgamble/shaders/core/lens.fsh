#version 330

// Liquid Glass lens, the prototype's LensGL shader line for line. Sampler0 = a copy of the screen taken just before
// this draw. Rest = a frosted pill; pressed = a clear lens whose rim bends what is behind it.
uniform sampler2D Sampler0;

in vec2 local;
flat in vec2 lensSize;
flat in float k;
flat in float mag;
flat in float pressed;
flat in float lean;

out vec4 fragColor;

float sdBox(vec2 d, vec2 b, float r) { vec2 q = abs(d) - b + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }
// the jelly pill: its half height swells toward the side it moves to (lean -1..1) and thins behind
float sdLens(vec2 d, vec2 b) { float hh = b.y * (1.0 + 0.3 * lean * clamp(d.x / b.x, -1.0, 1.0)); return sdBox(d, vec2(b.x, hh), hh); }

// a point given relative to the lens centre (y down) -> the screen copy, through this fragment's own position
vec3 tap(vec2 s) {
    vec2 px = gl_FragCoord.xy + vec2(s.x - local.x, local.y - s.y);
    vec2 sz = vec2(textureSize(Sampler0, 0));
    return texture(Sampler0, clamp(px / sz, vec2(0.0), vec2(1.0))).rgb;
}

vec3 frost(vec2 p, float r) {   // 7 taps: the centre and a hexagon (frosted glass)
    vec3 c = tap(p) * 2.0;
    c += tap(p + vec2(r, 0.0)) + tap(p - vec2(r, 0.0));
    c += tap(p + vec2(0.5 * r, 0.866 * r)) + tap(p + vec2(-0.5 * r, 0.866 * r));
    c += tap(p + vec2(0.5 * r, -0.866 * r)) + tap(p + vec2(-0.5 * r, -0.866 * r));
    return c / 8.0;
}

void main() {
    vec2 d = local, b = lensSize * 0.5;
    float sd = sdLens(d, b);
    // soft diffuse drop shadow just under the lens
    float sds = sdLens(d - vec2(0.0, 0.10 * lensSize.y), b);
    float sh = (1.0 - smoothstep(-0.15 * lensSize.y, 0.45 * lensSize.y, sds)) * (0.20 + 0.18 * pressed);
    float aa = clamp(0.5 - sd, 0.0, 1.0);
    if (aa <= 0.0) {
        if (sh <= 0.002) discard;
        fragColor = vec4(0.0, 0.0, 0.0, sh);
        return;
    }
    float e = 0.75;
    vec2 n = vec2(sdLens(d + vec2(e, 0.0), b) - sdLens(d - vec2(e, 0.0), b), sdLens(d + vec2(0.0, e), b) - sdLens(d - vec2(0.0, e), b));
    n = normalize(n + vec2(1e-5));
    // refraction: uniform magnification in the middle, the rim bends the light along the normal (convex lens)
    float bw = 0.28 * lensSize.y * (1.0 + 0.25 * pressed);
    float t = clamp(-sd / bw, 0.0, 1.0);
    float edge = pow(1.0 - t, 2.5);
    vec2 base = d / mag;
    vec2 o = n * bw * (0.4 + 3.2 * pressed) * edge;
    float ca = 0.05 + 0.40 * pressed;                   // chromatic aberration: R, G, B bent a little differently
    float fr = k * mix(1.3, 0.9, pressed);             // frost radius: dull at rest, clear when pressed
    vec3 col = vec3(frost(base + o * (1.0 + ca), fr).r, frost(base + o, fr).g, frost(base + o * (1.0 - ca), fr).b);
    // tint: a little lighter and more saturated inside, a white veil at rest (the frosted pill)
    float l = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(l), col, 1.15);
    col += (1.0 - col) * mix(0.16, 0.02, pressed);   // pressed: almost clear glass
    // thickness: the inner rim slightly darker
    col *= 1.0 - 0.13 * edge;
    // highlights: a thin bright rim top left, a softer one bottom right
    vec2 L = normalize(vec2(-1.0, -1.2));
    float rim1 = exp(sd / (1.1 * k)), rim2 = exp(sd / (2.6 * k));
    col += pow(max(dot(n, L), 0.0), 5.0) * rim1 * (0.45 + 0.45 * pressed);
    col += pow(max(dot(n, -L), 0.0), 3.0) * rim2 * (0.12 + 0.14 * pressed);
    // pressed: two bright glints on the diagonal, bottom left and top right, like the iOS lens
    vec2 G = normalize(vec2(-1.0, 1.0));
    col += (pow(max(dot(n, G), 0.0), 4.0) + pow(max(dot(n, -G), 0.0), 4.0)) * exp(sd / (1.6 * k)) * 0.75 * pressed;
    col += rim1 * 0.10 * pressed;
    // held: an iridescent rim whose hue turns with the angle around the lens (the iOS rainbow edge)
    vec3 iri = 0.5 + 0.5 * cos(atan(n.y, n.x) * 2.0 + vec3(0.0, 2.094, 4.189));
    col += iri * exp(sd / (1.3 * k)) * 0.35 * pressed;
    // straight alpha for the TRANSLUCENT blend: the lens over its own shadow
    float a = aa + sh * (1.0 - aa);
    fragColor = vec4(min(col, vec3(1.0)) * aa / a, a);
}
