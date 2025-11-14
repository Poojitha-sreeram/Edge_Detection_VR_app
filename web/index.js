class FrameProcessor {
    constructor(canvasId) {
        this.canvas = document.getElementById(canvasId);
        this.ctx = this.canvas.getContext('2d');
        this.frameCount = 0;
        this.lastTime = Date.now();
        this.fps = 0;
        this.processingMode = 'edge';
    }

    updateFPS() {
        this.frameCount++;
        const now = Date.now();
        if (now - this.lastTime >= 1000) {
            this.fps = this.frameCount;
            this.frameCount = 0;
            this.lastTime = now;
            this.updateStats();
        }
    }

    displayFrame(imageData) {
        this.ctx.putImageData(imageData, 0, 0);
        this.updateFPS();
    }

    displayFrameFromUrl(url) {
        const img = new Image();
        img.onload = () => {
            this.ctx.drawImage(img, 0, 0, this.canvas.width, this.canvas.height);
            this.updateFPS();
        };
        img.src = url;
    }

    applyEdgeDetection(imageData) {
        const data = imageData.data;
        const result = new ImageData(imageData.width, imageData.height);
        const resultData = result.data;

        for (let i = 0; i < data.length; i += 4) {
            const gray = Math.round(0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2]);
            resultData[i] = gray;
            resultData[i + 1] = gray;
            resultData[i + 2] = gray;
            resultData[i + 3] = 255;
        }

        return result;
    }

    applyGrayscale(imageData) {
        const data = imageData.data;
        const result = new ImageData(imageData.width, imageData.height);
        const resultData = result.data;

        for (let i = 0; i < data.length; i += 4) {
            const gray = Math.round(0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2]);
            resultData[i] = gray;
            resultData[i + 1] = gray;
            resultData[i + 2] = gray;
            resultData[i + 3] = 255;
        }

        return result;
    }

    toggleProcessingMode() {
        this.processingMode = this.processingMode === 'edge' ? 'raw' : 'edge';
        document.getElementById('mode').textContent = this.processingMode === 'edge' ? 'Edge Detection' : 'Raw Feed';
    }

    updateStats() {
        document.getElementById('fps').textContent = this.fps;
        document.getElementById('processingTime').textContent = (1000 / this.fps).toFixed(2) + 'ms';
    }

    clearFrame() {
        this.ctx.fillStyle = '#000000';
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
    }
}

// Global instance
let frameProcessor;

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    frameProcessor = new FrameProcessor('frameCanvas');
});

// UI Functions
function loadSampleFrame() {
    // Generate a sample processed frame (edge detection simulation)
    const canvas = document.getElementById('frameCanvas');
    const ctx = canvas.getContext('2d');
    
    // Create a gradient for demonstration
    const gradient = ctx.createLinearGradient(0, 0, canvas.width, canvas.height);
    gradient.addColorStop(0, '#000000');
    gradient.addColorStop(0.5, '#808080');
    gradient.addColorStop(1, '#ffffff');
    
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    
    // Draw some simulated edge lines
    ctx.strokeStyle = '#00ff00';
    ctx.lineWidth = 2;
    for (let i = 0; i < canvas.width; i += 50) {
        ctx.beginPath();
        ctx.moveTo(i, 0);
        ctx.lineTo(i + 30, canvas.height);
        ctx.stroke();
    }
    
    frameProcessor.updateFPS();
}

function toggleMode() {
    frameProcessor.toggleProcessingMode();
}

function clearFrame() {
    frameProcessor.clearFrame();
}

function handleImageUpload(event) {
    const file = event.target.files[0];
    if (file) {
        const reader = new FileReader();
        reader.onload = (e) => {
            frameProcessor.displayFrameFromUrl(e.target.result);
        };
        reader.readAsDataURL(file);
    }
}
