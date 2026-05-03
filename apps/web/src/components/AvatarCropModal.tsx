import React, { useCallback, useEffect, useRef, useState } from 'react';
import { X, ZoomIn, ZoomOut, Upload } from 'lucide-react';

interface AvatarCropModalProps {
  file: File;
  onConfirm: (blob: Blob) => void;
  onCancel: () => void;
}

const CANVAS_SIZE = 320;
const MIN_ZOOM = 1;
const MAX_ZOOM = 4;

export function AvatarCropModal({ file, onConfirm, onCancel }: AvatarCropModalProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const imgRef    = useRef<HTMLImageElement | null>(null);

  const [zoom,    setZoom]    = useState(1);
  const [offset,  setOffset]  = useState({ x: 0, y: 0 });
  const [drag,    setDrag]    = useState<{ startX: number; startY: number; startOX: number; startOY: number } | null>(null);
  const [loading, setLoading] = useState(false);

  // Load image from file
  useEffect(() => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      imgRef.current = img;
      // Auto-fit: zoom so image fills the circle
      const fit = Math.max(CANVAS_SIZE / img.naturalWidth, CANVAS_SIZE / img.naturalHeight);
      setZoom(fit);
      setOffset({ x: 0, y: 0 });
    };
    img.src = url;
    return () => URL.revokeObjectURL(url);
  }, [file]);

  // Redraw canvas whenever zoom/offset changes
  const draw = useCallback(() => {
    const canvas = canvasRef.current;
    const img    = imgRef.current;
    if (!canvas || !img) return;

    const ctx = canvas.getContext('2d')!;
    ctx.clearRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);

    const w = img.naturalWidth  * zoom;
    const h = img.naturalHeight * zoom;
    const x = CANVAS_SIZE / 2 - w / 2 + offset.x;
    const y = CANVAS_SIZE / 2 - h / 2 + offset.y;

    // Draw image
    ctx.drawImage(img, x, y, w, h);

    // Darken outside circle
    ctx.fillStyle = 'rgba(0,0,0,0.55)';
    ctx.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);
    ctx.globalCompositeOperation = 'destination-out';
    ctx.beginPath();
    ctx.arc(CANVAS_SIZE / 2, CANVAS_SIZE / 2, CANVAS_SIZE / 2 - 4, 0, Math.PI * 2);
    ctx.fill();
    ctx.globalCompositeOperation = 'source-over';

    // Redraw image inside circle only
    ctx.save();
    ctx.beginPath();
    ctx.arc(CANVAS_SIZE / 2, CANVAS_SIZE / 2, CANVAS_SIZE / 2 - 4, 0, Math.PI * 2);
    ctx.clip();
    ctx.drawImage(img, x, y, w, h);
    ctx.restore();

    // Circle border
    ctx.strokeStyle = 'rgba(124,92,252,0.8)';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.arc(CANVAS_SIZE / 2, CANVAS_SIZE / 2, CANVAS_SIZE / 2 - 4, 0, Math.PI * 2);
    ctx.stroke();
  }, [zoom, offset]);

  useEffect(() => { draw(); }, [draw]);

  // Mouse drag
  const onMouseDown = (e: React.MouseEvent) => {
    setDrag({ startX: e.clientX, startY: e.clientY, startOX: offset.x, startOY: offset.y });
  };
  const onMouseMove = useCallback((e: MouseEvent) => {
    if (!drag) return;
    setOffset({ x: drag.startOX + e.clientX - drag.startX, y: drag.startOY + e.clientY - drag.startY });
  }, [drag]);
  const onMouseUp = useCallback(() => setDrag(null), []);

  useEffect(() => {
    window.addEventListener('mousemove', onMouseMove);
    window.addEventListener('mouseup',   onMouseUp);
    return () => { window.removeEventListener('mousemove', onMouseMove); window.removeEventListener('mouseup', onMouseUp); };
  }, [onMouseMove, onMouseUp]);

  // Scroll to zoom
  const onWheel = (e: React.WheelEvent) => {
    e.preventDefault();
    setZoom((z) => Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, z - e.deltaY * 0.002)));
  };

  // Export cropped circle as blob
  const exportBlob = (): Promise<Blob> => {
    return new Promise((resolve, reject) => {
      const out  = document.createElement('canvas');
      out.width  = 256;
      out.height = 256;
      const ctx  = out.getContext('2d')!;
      const img  = imgRef.current!;
      const scale = 256 / CANVAS_SIZE;

      ctx.beginPath();
      ctx.arc(128, 128, 128, 0, Math.PI * 2);
      ctx.clip();

      const w = img.naturalWidth  * zoom  * scale;
      const h = img.naturalHeight * zoom  * scale;
      const x = 128 - w / 2 + offset.x * scale;
      const y = 128 - h / 2 + offset.y * scale;
      ctx.drawImage(img, x, y, w, h);

      out.toBlob((b) => b ? resolve(b) : reject(new Error('Export failed')), 'image/webp', 0.9);
    });
  };

  const handleConfirm = async () => {
    setLoading(true);
    try {
      const blob = await exportBlob();
      onConfirm(blob);
    } catch {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        position: 'fixed', inset: 0, zIndex: 400,
        background: 'rgba(0,0,0,0.85)', backdropFilter: 'blur(6px)',
        display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
        animation: 'settingsPageIn 180ms ease forwards',
      }}
    >
      <div style={{ background: 'var(--bg-elevated)', borderRadius: 16, padding: 28, width: 380, border: '1px solid var(--border-subtle)', boxShadow: '0 24px 60px rgba(0,0,0,0.5)' }}>
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: 'var(--text-primary)' }}>Edit Profile Photo</h3>
          <button onClick={onCancel} style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', display: 'flex', padding: 4 }}>
            <X size={18} />
          </button>
        </div>

        {/* Canvas */}
        <canvas
          ref={canvasRef}
          width={CANVAS_SIZE}
          height={CANVAS_SIZE}
          onMouseDown={onMouseDown}
          onWheel={onWheel}
          style={{ borderRadius: 8, cursor: drag ? 'grabbing' : 'grab', display: 'block', width: '100%', userSelect: 'none' }}
        />

        {/* Zoom slider */}
        <div style={{ marginTop: 16, display: 'flex', alignItems: 'center', gap: 10 }}>
          <ZoomOut size={16} style={{ color: 'var(--text-muted)', flexShrink: 0 }} />
          <input
            type="range" min={MIN_ZOOM} max={MAX_ZOOM} step={0.01}
            value={zoom}
            onChange={(e) => setZoom(parseFloat(e.target.value))}
            style={{ flex: 1, accentColor: 'var(--accent)' }}
          />
          <ZoomIn size={16} style={{ color: 'var(--text-muted)', flexShrink: 0 }} />
        </div>

        <p style={{ fontSize: 12, color: 'var(--text-muted)', textAlign: 'center', margin: '8px 0 0' }}>
          Drag to reposition · Scroll or use slider to zoom
        </p>

        {/* Actions */}
        <div style={{ display: 'flex', gap: 10, marginTop: 20 }}>
          <button
            onClick={onCancel}
            style={{ flex: 1, padding: '10px', borderRadius: 'var(--radius-md)', border: '1px solid var(--border)', background: 'transparent', color: 'var(--text-secondary)', fontFamily: 'inherit', fontSize: 14, cursor: 'pointer' }}
          >
            Cancel
          </button>
          <button
            onClick={handleConfirm}
            disabled={loading}
            style={{ flex: 1, padding: '10px', borderRadius: 'var(--radius-md)', border: 'none', background: 'var(--accent)', color: '#fff', fontFamily: 'inherit', fontSize: 14, fontWeight: 600, cursor: loading ? 'not-allowed' : 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6, opacity: loading ? 0.7 : 1 }}
          >
            {loading ? (
              <span style={{ width: 14, height: 14, border: '2px solid rgba(255,255,255,0.4)', borderTopColor: '#fff', borderRadius: '50%', animation: 'spin 0.7s linear infinite', display: 'inline-block' }} />
            ) : <Upload size={14} />}
            {loading ? 'Uploading…' : 'Apply'}
          </button>
        </div>
      </div>
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}
