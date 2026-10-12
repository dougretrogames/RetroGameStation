#ifndef PPSSPP_CORE_H__
#define PPSSPP_CORE_H__

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

// PSP Key definitions
#define CTRL_SELECT      0x00000001
#define CTRL_START       0x00000008
#define CTRL_UP          0x00000010
#define CTRL_RIGHT       0x00000020
#define CTRL_DOWN        0x00000040
#define CTRL_LEFT        0x00000080
#define CTRL_LTRIGGER    0x00000100
#define CTRL_RTRIGGER    0x00000200
#define CTRL_TRIANGLE    0x00001000
#define CTRL_CIRCLE      0x00002000
#define CTRL_CROSS       0x00004000
#define CTRL_SQUARE      0x00008000

// Bundled internal dependency symbols (e.g. FFmpeg, zlib) isolated within PPSSPP
// These are intentionally present to verify symbol hiding and map scripts isolate them.
void avcodec_register_all_stub();
const char* avcodec_configuration_stub();
int zlib_deflate_stub(void* strm, int flush);

// PPSSPP Core simulation API
bool NativeInit(int argc, const char* argv[]);
void NativeInitGraphics();
void NativeRender();
void NativeUpdate();
void NativeShutdownGraphics();
void NativeShutdown();

void NativeKey(int key, int down);
void NativeAxis(int axisId, float value);
int NativeAudio(int16_t* audioBuffer, int numSamples);

#ifdef __cplusplus
}
#endif

#endif // PPSSPP_CORE_H__
