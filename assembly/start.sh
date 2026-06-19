#!/bin/sh
SCRIPTPATH=$(dirname "$0")
$SCRIPTPATH/jre21/bin/java -Xmx4g -cp $SCRIPTPATH/geohammer-jar-with-dependencies.jar com.ugcs.geohammer.MainGeoHammer
