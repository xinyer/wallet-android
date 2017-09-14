#!/bin/bash

render(){
  in=$1
  out=$2
  # AAAHHH bash! http://stackoverflow.com/questions/1063347/passing-arrays-as-parameters-in-bash
  sizes=("${!3}")
  sizeNames=("${!4}")
    
  for ((n=0; n < 3; n++))
  do
    echo "convert -background none -resize "${sizes[n]}"x "$in" - | pngquant --force 64 > ../src/main/res/drawable-"${sizeNames[n]}"/"$out
    convert -background none -resize ${sizes[n]}"x" $in - | pngquant --force 64 > "../src/main/res/drawable-"${sizeNames[n]}"/"$out
  done
}

ss=(18 24 36)
sNs=(ldpi mdpi hdpi)
render stat_sys_peers_0.png stat_sys_peers_0.png ss[@] sNs[@]
render stat_sys_peers_1.png stat_sys_peers_1.png ss[@] sNs[@]
render stat_sys_peers_2.png stat_sys_peers_2.png ss[@] sNs[@]
render stat_sys_peers_3.png stat_sys_peers_3.png ss[@] sNs[@]
render stat_sys_peers_4.png stat_sys_peers_4.png ss[@] sNs[@]

ss=(48 72 96 144 192)
sNs=(mdpi hdpi xhdpi xxhdpi xxxhdpi)
render logo.png ic_launcher.png ss[@] sNs[@]
