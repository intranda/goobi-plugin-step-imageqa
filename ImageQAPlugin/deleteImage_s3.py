#!/usr/bin/env python3

import os, sys

	config_file = "/opt/digiverso/goobi/config/goobi_config.properties"

def main():
	
	print("move image into backup")
	
	#get destination key
	dest_uri = get_deleted_prefix(sys.argv[2])
	s3_move(sys.argv[1], dest_uri)
	
	print("moving image finished")

def get_deleted_prefix(orig_prefix):
	if orig_prefix.endswith("/"):
		orig_prefix = orig_prefix[:-1]
	return orig_prefix[:orig_prefix.rfind("/")] + "/DELETED/"

def s3_move(source, target):
	goobi_config = load_properties(config_file)
	s3_command = ["aws"]
	env = {}
	if "useCustomS3" in goobi_config and goobi_config["useCustomS3"].lower() == "true":
		s3_command.extend(["--endpoint-url", goobi_config["S3Endpoint"]])
		env["AWS_ACCESS_KEY_ID"] = goobi_config["S3AccessKeyID"]
		env["AWS_SECRET_ACCESS_KEY"] = goobi_config["S3SecretAccessKey"]
	
	cp_command = s3_command
	cp_command.extend(["cp", source, target])
	subprocess.call(cp_command, env=env)
	
	rm_command = s3_command
	rm_command.extend(["rm", source])
	subprocess.call(rm_command, env=env)

def load_properties(filepath, sep='=', comment_char='#'):
    """
    Read the file passed as parameter as a properties file.
    """
    props = {}
    with open(filepath, "rt") as f:
        for line in f:
            l = line.strip()
            if l and not l.startswith(comment_char):
                key_value = l.split(sep)
                key = key_value[0].strip()
                value = sep.join(key_value[1:]).strip().strip('"') 
                props[key] = value 
    return props
    
if __name__ == "__main__":
    main()
