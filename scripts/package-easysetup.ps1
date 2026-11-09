param([string]$Name='VideoQuiz-Web-EasySetup.zip')
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
if($Name -notmatch '^[A-Za-z0-9_-]+\.zip$'){throw 'Use a plain ZIP filename.'}
Add-Type -AssemblyName System.IO.Compression
New-Item -ItemType Directory -Path (Join-Path $root 'dist') -Force | Out-Null
$output=Join-Path $root ('dist/'+$Name)
if(Test-Path -LiteralPath $output){throw 'Archive already exists; choose a new filename to preserve it.'}
$files=@('pom.xml','nbactions.xml','README.md','HOW_TO_RUN_IN_NETBEANS.md','DEPLOYMENT.md','.gitignore','.dockerignore','Dockerfile','render.yaml','setup-videoquiz.bat','start-videoquiz.bat','config/settings.example.json','lib/gson-2.13.2.jar')
foreach($folder in @('src','tests','scripts','.github','docs')){
    $files+=Get-ChildItem -LiteralPath (Join-Path $root $folder) -Recurse -File | Where-Object { $_.Extension -in @('.java','.js','.cjs','.css','.html','.properties','.ps1','.md','.yml','.yaml','.pdf') -or $_.Name -eq 'org.mockito.plugins.MockMaker' } | ForEach-Object { $_.FullName.Substring($root.Length+1).Replace('\','/') }
}
$stream=[IO.File]::Open($output,[IO.FileMode]::CreateNew)
$zip=New-Object IO.Compression.ZipArchive($stream,[IO.Compression.ZipArchiveMode]::Create)
try{
    foreach($relative in ($files|Sort-Object -Unique)){
        $source=Join-Path $root $relative
        if(-not(Test-Path -LiteralPath $source)){throw ('Missing required file: '+$relative)}
        if([IO.Path]::GetExtension($source) -notin @('.jar','.pdf')){
            $text=[IO.File]::ReadAllText($source)
            if($text -match 'AIza[0-9A-Za-z_-]{35}|-----BEGIN [A-Z ]*PRIVATE KEY-----|eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]+'){throw ('Secret-like value detected in '+$relative)}
        }
        $entry=$zip.CreateEntry('VideoQuiz-Web/'+$relative,[IO.Compression.CompressionLevel]::Optimal)
        $input=[IO.File]::OpenRead($source);$destination=$entry.Open()
        try{$input.CopyTo($destination)}finally{$input.Dispose();$destination.Dispose()}
    }
}finally{$zip.Dispose();$stream.Dispose()}
Write-Host ('Created '+$output)
