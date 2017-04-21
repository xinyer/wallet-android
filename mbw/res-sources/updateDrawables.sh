#!/bin/bash

sizeNames=(ldpi mdpi hdpi xhdpi xxhdpi)
sizesBig=(360 480 720 960 1800)
sizesIcon=(36 48 72 96 180)

render(){
  in=$1
  out=$2
  # AAAHHH bash! http://stackoverflow.com/questions/1063347/passing-arrays-as-parameters-in-bash
  sizes=("${!3}")
  
  for ((n=0; n <= 4; n++))
  do
    echo "convert -background none -resize "${sizes[n]}"x "$in" - | pngquant --force 64 > ../src/main/res/drawable-"${sizeNames[n]}"/"$out
    convert -background none -resize ${sizes[n]}"x" $in - | pngquant --force 64 > "../src/main/res/drawable-"${sizeNames[n]}"/"$out
  done
}

render localTraderLocalOnly.png lt_local_only_warning.png sizesBig[@]
render permissiongroupprivacy.svg permissiongroupprivacy.png sizesIcon[@]

sizes=(100 133 200 267 400)
fileIn=creditCard.png
fileOut=credit_card_buy.png

for ((n=0; n <= 4; n++))
do
    fOut="../src/main/res/drawable-"${sizeNames[n]}"/"$fileOut
    convert -background none -resize ${sizes[n]}"x" $fileIn - | \
        pngquant --force 64 > $fOut
done

sizes=(100 133 200 267 400)
fileIn=mycelium_logo_transp.png
fileOut=mycelium_logo_transp.png

for ((n=0; n <= 4; n++))
do
    fOut="../src/main/res/drawable-"${sizeNames[n]}"/"$fileOut
    convert -background none -resize ${sizes[n]}"x" $fileIn - | \
        pngquant --force 64 > $fOut
done