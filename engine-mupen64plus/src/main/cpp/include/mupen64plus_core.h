#ifndef MUPEN64PLUS_CORE_H__
#define MUPEN64PLUS_CORE_H__

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

// N64 Button bitmasks
#define N64_CONT_A      0x8000
#define N64_CONT_B      0x4000
#define N64_CONT_Z      0x2000
#define N64_CONT_START  0x1000
#define N64_CONT_UP     0x0800
#define N64_CONT_DOWN   0x0400
#define N64_CONT_LEFT   0x0200
#define N64_CONT_RIGHT  0x0100
#define N64_CONT_L      0x0020
#define N64_CONT_R      0x0010
#define N64_CONT_C_UP   0x0008
#define N64_CONT_C_DOWN 0x0004
#define N64_CONT_C_LEFT 0x0002
#define N64_CONT_C_RIGHT 0x0001

typedef enum {
    M64CMD_NOP = 0,
    M64CMD_ROM_OPEN,
    M64CMD_ROM_CLOSE,
    M64CMD_ROM_GET_HEADER,
    M64CMD_EXECUTE,
    M64CMD_STOP,
    M64CMD_PAUSE,
    M64CMD_RESUME,
    M64CMD_STATE_SAVE,
    M64CMD_STATE_LOAD
} m64p_command;

typedef enum {
    M64ERR_SUCCESS = 0,
    M64ERR_NOT_INIT,
    M64ERR_ALREADY_INIT,
    M64ERR_INCOMPATIBLE,
    M64ERR_INPUT_INVALID,
    M64ERR_INPUT_NOT_FOUND,
    M64ERR_NO_MEMORY,
    M64ERR_FILES
} m64p_error;

// Bundled internal dependency symbols (e.g. SDL2, libpng, zlib) isolated within Mupen64Plus
// These symbols must remain local and unexported to avoid symbol collisions with other engines.
int SDL_Init_stub(uint32_t flags);
void* png_create_read_struct_stub(const char* user_png_ver, void* error_ptr, void* error_fn, void* warn_fn);
int zlib_inflate_stub(void* strm, int flush);

// Mupen64Plus API definitions
m64p_error CoreStartup(int apiVersion, const char* configPath, const char* dataPath, void* context,
                       void (*debugCallback)(void*, int, const char*));
m64p_error CoreShutdown();
m64p_error CoreDoCommand(m64p_command command, int intParam, void* ptrParam);

#ifdef __cplusplus
}
#endif

#endif // MUPEN64PLUS_CORE_H__
