// an extra wrapper is needed here since Chisel won't let us manually add
// renamed clock ports that connect to the global clock
// so the only thing this module does is to connect the single clock input
// clk to the multiple clock inputs clka and clkb

module ResultBRAM (
  clk,
  wea,
  addra,
  dina,
  douta,
  web,
  addrb,
  dinb,
  doutb
);

input clk;
input [0 : 0] wea;
input [19 : 0] addra;
input [0 : 0] dina;
output [31 : 0] douta;
input [0 : 0] web;
input [19 : 0] addrb;
input [0 : 0] dinb;
output [31 : 0] doutb;

ResultBRAMV7690T bramInst (
  .clka(clk),
  .wea(wea),
  .addra(addra),
  .dina(dina),
  .douta(douta),
  .clkb(clk),
  .web(web),
  .addrb(addrb),
  .dinb(dinb),
  .doutb(doutb)
);

endmodule
