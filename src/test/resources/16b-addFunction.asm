CLS
CALL main
EXIT

main:
	; x =
	LD Rx, $FF
	LD Ry, $00
	LD I, hexAdd_x
	LD I, Rx, Ry

	; y =
	LD Rx, $00
	LD Ry, $01
	LD I, hexAdd_y
	LD I, Rx, Ry

	; x + Y
	CALL hexAdd

	; put outcome into memory
	LD I, hexAdd_dest_1
	LD Rx, Ry, I
	LD I, $0300
	LD I, Rx, Ry

	RET

;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;     16-bit add     ;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;

hexAdd_x:
	DB $00
	DB $00
hexAdd_y:
	DB $00
	DB $00
hexAdd_dest_1:
	DB $00
hexAdd_dest_0:
	DB $00

hexAdd: ; hexAdd_x = hexAdd_x + hexAdd_y
	LD I, hexAdd_x
	LD Rx, $01
	ADD I, Rx
	LD Ry, I
	LD I, hexAdd_y
	ADD I, Rx
	LD Rx, I

	ADD Rx, Ry

	LD I, hexAdd_dest_0
	LD I, Rx

	LD I, hexAdd_x
	LD Rx, I

	LD I, overflowAdd
	JP I, F					; if there was an overflow
	LD I, overflowAddEnd
	JP overflowAddEnd
	overflowAdd:
		ADD Rx, $01			; add 1 to next byte
	overflowAddEnd:
	LD I, hexAdd_y
	LD Ry, I

	ADD Rx, Ry

	LD I, hexAdd_dest_1
	LD I, Ry

	RET