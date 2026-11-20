# Pool Overlay Robot (Android Project - v7)

Complete real-time screen capture + overlay aim line app connected to `https://pool-027f.onrender.com/api/calculate`:
- `ScreenCaptureService.java` receives `RESULT_CODE` and `DATA` from `MediaProjectionManager`, creates a `VirtualDisplay` + `ImageReader`, captures frames every ~1.2 seconds (avoiding overlapping requests), converts `Image` to `Bitmap`, and sends it to `sendToAPI(bitmap)`.
- Automatically scales coordinates returned by the server (`1600x720` or whatever capture size was sent) to match the full `OverlayView` dimensions so the drawn line aligns accurately with the balls on screen.
- Includes a floating **"SCAN"** button on the overlay (as well as automatic periodic scanning) so you can trigger an instant scan right inside the pool game.
