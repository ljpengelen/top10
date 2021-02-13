# Top 10 - Infrastructure

## Getting Started

1. Obtain the SSH keys to access the VMs as administrator and store them as `~/.ssh/linode_cofx_ed25519.pub` and `~/.ssh/linode_cofx_ed25519`.
  Alternatively, create new keys and put them in the location specified above.
1. Set up two VMs running Ubuntu 20.
1. Ensure that the root user is able to access these VMs via SSH using the public key for the administrator listed above.
1. Configure SSH to use the administrator keys mentioned above to access the VMs.
1. Obtain the SSH keys to access the Git repositories managed by Dokku and store them as `~/.ssh/linode_cofx_git_ed25519.pub` and `~/.ssh/linode_cofx_git_ed25519`.
  Alternatively, create new keys and put them in the location specified above.
1. Obtain the file `ansible/secrets.yml`, containing the username and password for the administrator account.
1. Install [Ansible](https://www.ansible.com/).

## Set up (non-root) administrator

1. Enter the folder `ansible`.
1. Execute `ansible-playbook -i <environment> -e @secrets.yml set_up_admin_user.yml` to set up Dokku, where `<environment>` should be either `acc` or `prod`.

## Secure SSH

1. Enter the folder `ansible`.
1. Execute `ansible-playbook -i <environment> -e @secrets.yml secure_server.yml` to set up Dokku, where `<environment>` should be either `acc` or `prod`.

After executing this playbook, you can no longer execute the previous one because the first playbook logs in as root.

## Set up Dokku

1. Enter the folder `ansible`.
1. Execute `ansible-playbook -i <environment> -e @secrets.yml dokku.yml` to set up Dokku, where `<environment>` should be either `acc` or `prod`.

## Giving Jenkins access to Dokku repositories

After setting up Dokku, you need to ensure that Jenkins is able to access the Git repositories managed by Dokku.

1. Log in to the machine running Jenkins.
1. Clone the repository for the front-end to a temporary folder by executing `git clone dokku@<hostname-for-environment>:front-end /tmp/deploy-front-end`, where `<hostname-for-environment>` is either `acceptance.cofx.nl` or `cofx.nl`.

If this succeeds without any errors, Jenkins is able to access the Git repositories managed by Dokku.
If not, one or more of the following issues must be resolved.

* If you see an error regarding host key verification, ensure that the new host key is correct and add it to the `known_hosts` configuration file for SSH.
* If you see a warning stating that the authenticity of the host cannot be established, ensure that the host key is correct and continue connecting.
* If permission to read the repository is denied, update the configuration of SSH to use the correct key.
