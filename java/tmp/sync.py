import shutil

for f in ["hosts.html", "app.css"]:
    src = rf"d:\3\code\java\src\main\resources\static\pages\assets\{f}" if "hosts" in f else rf"d:\3\code\java\src\main\resources\static\css\{f}"
    dst = src.replace("src\\main\\resources", "target\\classes")
    shutil.copy2(src, dst)
    print(f"Synced: {f}")