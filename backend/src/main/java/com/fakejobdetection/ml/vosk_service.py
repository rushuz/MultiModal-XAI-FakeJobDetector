from flask import Flask, request, jsonify
import vosk, wave, json, io

app = Flask(__name__)
model = vosk.Model("vosk-model-small-en-us-0.15")

@app.route("/transcribe", methods=["POST"])
def transcribe():
    audio_bytes = request.json["audio"]
    wf = wave.open(io.BytesIO(audio_bytes), "rb")

    rec = vosk.KaldiRecognizer(model, wf.getframerate())
    while True:
        data = wf.readframes(4000)
        if len(data) == 0:
            break
        rec.AcceptWaveform(data)

    return jsonify(json.loads(rec.FinalResult()))

app.run(port=6000)