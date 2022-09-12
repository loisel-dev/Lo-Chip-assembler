CLS
CALL main
EXIT

x:
	DB $20
y:
	DB $21
result:
	DB $41
output:
	DB $FF


main:
	LD Rx, $20
	LD Ry, $21

	ADD Rx, Ry

	LD I, result
	LD Ry, I

	;if(
	LD I, endif1
	JNE Rx, Ry
	;) then:
		LD I, output
		LD I, Rx
	endif1:		;endif

	RET