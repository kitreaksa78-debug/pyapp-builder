[app]
title = My Python App
package.name = telegrambot
package.domain = com.aline
source.dir = .
source.include_exts = py,png,jpg,kv,atlas,json,ttf
source.exclude_dirs = .git,.github,.buildozer,bin,out,jobs,templates,static,__pycache__,venv
source.exclude_patterns = README.md,*.yml,*.yaml,requirements.txt,render.yaml,.gitignore
version = 1.0
requirements = python3,kivy
orientation = portrait
fullscreen = 0
android.permissions = INTERNET
android.accept_sdk_license = True
android.archs = arm64-v8a
android.api = 31
android.minapi = 21
android.ndk = 25b
p4a.branch = v2024.01.21

[buildozer]
log_level = 2
warn_on_root = 0
