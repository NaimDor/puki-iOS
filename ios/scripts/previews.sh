#!/bin/bash
# Turns the raw simulator recordings into App Store app previews: landscape, the store's size, 30 fps, H.264,
# 29 seconds (the store takes 15 to 30), with a silent stereo track. The first 1.5 s are cut (the app starting).
#   ios/scripts/previews.sh shots/previews-raw shots-previews
set -e
in=$1; out=$2
for f in "$in"/*.mov; do
  name=$(basename "$f" .mov)           # iphone_en-US, ipad_he ...
  tag=${name%%_*}; locale=${name#*_}
  # iPhone: recent simulators record the Dynamic Island (a black pill on the left edge), so zoom in a little
  # until it's out of the picture
  if [ "$tag" = iphone ]; then size=1920:886; zoom="crop=iw*0.87:ih*0.87,"; else size=1600:1200; zoom=""; fi
  w=$(ffprobe -v error -select_streams v:0 -show_entries stream=width -of csv=p=0 "$f")
  h=$(ffprobe -v error -select_streams v:0 -show_entries stream=height -of csv=p=0 "$f")
  turn=""; if [ "$h" -gt "$w" ]; then turn="transpose=2,"; fi   # the simulator records upright; the game is on its side
  mkdir -p "$out/$locale"
  ffmpeg -loglevel error -y -ss 1.5 -i "$f" -f lavfi -i anullsrc=r=48000:cl=stereo -t 29 \
    -vf "${turn}${zoom}scale=${size}:force_original_aspect_ratio=increase,crop=${size},fps=30,format=yuv420p" \
    -c:v libx264 -profile:v high -level 4.0 -b:v 10M -maxrate 12M -bufsize 20M \
    -c:a aac -b:a 256k -ar 48000 -shortest -movflags +faststart "$out/$locale/${tag}_preview.mp4"
  echo "$out/$locale/${tag}_preview.mp4: $(ffprobe -v error -show_entries format=duration -of csv=p=0 "$out/$locale/${tag}_preview.mp4")s"
done
