set -ex

mkdir build
cd build

for i in ../lib/*.jar; do echo unjarring $i; jar xf $i; done

cd ../

kotlinc -include-runtime -d unplug-base.jar -cp build src/

cd build
jar xf ../unplug-base.jar
cp ../src/chat.css ../src/*.png ./

jar cfe ../unplug.jar co.uproot.unplug.GuiClientKt *

