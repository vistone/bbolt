#include <stdlib.h>
#include "types.h"

void bolt_Free(void* ptr) {
  free(ptr);
}

void bolt_Result_Free(Result result) {
  free(result.error);
}

void bolt_Error_Free(Error error) {
  free(error.error);
}

void bolt_Sequence_Free(Sequence sequence) {
  free(sequence.error);
}
