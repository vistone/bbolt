package main

// #include "types.h"
import "C"
import "unsafe"

//export Free
func Free(ptr unsafe.Pointer) {
	C.bolt_Free(ptr)
}

//export Result_Free
func Result_Free(result C.Result) {
	C.bolt_Result_Free(result)
}

//export Error_Free
func Error_Free(err C.Error) {
	C.bolt_Error_Free(err)
}

//export Sequence_Free
func Sequence_Free(seq C.Sequence) {
	C.bolt_Sequence_Free(seq)
}
