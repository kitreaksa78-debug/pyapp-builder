var count = 0;
var out = document.getElementById("count");
document.getElementById("tap").addEventListener("click", function () {
  count += 1;
  out.textContent = count;
});
