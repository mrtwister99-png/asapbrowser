//! Generuje deterministický šum per profil+den a skutečný JS shim, který
//! patchuje `HTMLCanvasElement.toDataURL`/`getImageData` a `WebGL.readPixels`
//! přímo zde v `js_shim()` (žádný externí assets soubor) - injektuje se do
//! WebView přes RustBridge.antiFingerprintScript() -> evaluateJavascript.

use rand::rngs::StdRng;
use rand::{Rng, SeedableRng};

pub struct FingerprintNoise {
    seed: u64,
}

impl FingerprintNoise {
    /// Nový seed per profil + per den, aby fingerprint nebyl stabilní napříč dny,
    /// ale zůstal konzistentní v rámci jedné session (necuká UI).
    pub fn new(profile_id: i64, day_epoch: i64) -> Self {
        let seed = (profile_id as u64).wrapping_mul(0x9E3779B97F4A7C15) ^ (day_epoch as u64);
        FingerprintNoise { seed }
    }

    /// Vrátí offset -2..=2 pro každý RGBA kanál pixelu na pozici (x,y).
    pub fn pixel_offset(&self, x: u32, y: u32) -> (i8, i8, i8, i8) {
        let mut rng = StdRng::seed_from_u64(self.seed ^ ((x as u64) << 32 | y as u64));
        (
            rng.gen_range(-2..=2),
            rng.gen_range(-2..=2),
            rng.gen_range(-2..=2),
            0, // alpha kanál nechat netknutý
        )
    }

    /// Vrátí kompletní JS shim, který se injektuje do stránky (evaluateJavascript
    /// po onPageFinished) a SKUTEČNĚ patchuje canvas/WebGL čtecí API tak, aby
    /// vracely mírně zašuměná data - žádný placeholder, funkční patch:
    /// - `CanvasRenderingContext2D.getImageData` a `HTMLCanvasElement.toDataURL`
    ///   dostanou each-byte šum ±2 na RGB kanálech (alpha netknutá).
    /// - `WebGLRenderingContext.readPixels` stejně tak, pokud stránka WebGL používá.
    /// PRNG je mulberry32 (deterministický, seedovaný z `self.seed`) implementovaný
    /// přímo v JS, takže šum je stabilní v rámci jedné session/dne, ale liší se
    /// mezi profily a mezi dny (viz `new()`).
    pub fn js_shim(&self) -> String {
        format!(
            r#"(function() {{
    if (window.__myytbrowserFpPatched) return;
    window.__myytbrowserFpPatched = true;

    var seed = {seed};
    function mulberry32(a) {{
        return function() {{
            a |= 0; a = (a + 0x6D2B79F5) | 0;
            var t = Math.imul(a ^ (a >>> 15), 1 | a);
            t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
            return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
        }};
    }}
    var rand = mulberry32(seed);

    function noiseByte(b) {{
        var delta = Math.floor(rand() * 5) - 2; // -2..2
        var v = b + delta;
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }}

    function addNoise(imageData) {{
        var d = imageData.data;
        for (var i = 0; i < d.length; i += 4) {{
            d[i] = noiseByte(d[i]);
            d[i + 1] = noiseByte(d[i + 1]);
            d[i + 2] = noiseByte(d[i + 2]);
        }}
        return imageData;
    }}

    var origGetImageData = CanvasRenderingContext2D.prototype.getImageData;
    CanvasRenderingContext2D.prototype.getImageData = function() {{
        var result = origGetImageData.apply(this, arguments);
        return addNoise(result);
    }};

    var origToDataURL = HTMLCanvasElement.prototype.toDataURL;
    HTMLCanvasElement.prototype.toDataURL = function() {{
        try {{
            var ctx = this.getContext('2d');
            if (ctx) {{
                var w = this.width, h = this.height;
                var imageData = origGetImageData.call(ctx, 0, 0, w, h);
                addNoise(imageData);
                var tmp = document.createElement('canvas');
                tmp.width = w; tmp.height = h;
                tmp.getContext('2d').putImageData(imageData, 0, 0);
                return origToDataURL.apply(tmp, arguments);
            }}
        }} catch (e) {{}}
        return origToDataURL.apply(this, arguments);
    }};

    if (window.WebGLRenderingContext) {{
        var origReadPixels = WebGLRenderingContext.prototype.readPixels;
        WebGLRenderingContext.prototype.readPixels = function(x, y, width, height, format, type, pixels) {{
            origReadPixels.apply(this, arguments);
            if (pixels && pixels.length) {{
                for (var i = 0; i < pixels.length; i += 4) {{
                    pixels[i] = noiseByte(pixels[i]);
                    pixels[i + 1] = noiseByte(pixels[i + 1]);
                    pixels[i + 2] = noiseByte(pixels[i + 2]);
                }}
            }}
        }};
    }}
}})();"#,
            seed = self.seed as i64
        )
    }
}
