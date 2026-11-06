var box = document.getElementById("note");
var status = document.getElementById("status");
box.value = localStorage.getItem("note") || "";
box.addEventListener("input", function () {
  localStorage.setItem("note", box.value);
  status.textContent = "រក្សាទុករួច " + new Date().toLocaleTimeString();
});
