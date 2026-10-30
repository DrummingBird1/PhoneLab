# R8 is on for release builds (minify + resource shrinking).
#
# No blanket keep rules are needed: AAPT already keeps every class referenced from XML
# (activities/services/receivers in the manifest, custom views in layouts), and the
# AndroidX libraries in use (WorkManager workers, ViewModels) ship their
# own consumer keep rules. Add a rule here only for something reached by reflection.

# Keep file/line info so Play Console crash stack traces stay readable after
# de-obfuscation with the uploaded mapping.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
